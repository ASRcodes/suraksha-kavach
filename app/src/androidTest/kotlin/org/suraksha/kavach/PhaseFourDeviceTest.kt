package org.suraksha.kavach

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.suraksha.core.*
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PhaseFourDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as KavachTestApplication
    private val testAssets get() = InstrumentationRegistry.getInstrumentation().context.assets

    @Test fun eicarNormalFileAndRenamedNoCodeApkUseRealFileLogic() = runBlocking {
        // Official harmless 68-byte test. Generated in memory, never executed.
        val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*".toByteArray(Charsets.US_ASCII)
        assertEquals(68, eicar.size)
        assertEquals("275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f",
            MessageDigest.getInstance("SHA-256").digest(eicar).joinToString("") { "%02x".format(it) })
        val test = app.files.scanInput("renamed.apk") { eicar.inputStream() }
        assertEquals(Level.DANGEROUS, test.verdict.level)
        assertTrue(ReasonCode.EICAR_TEST in test.verdict.reasonCodes)
        assertEquals(Level.SAFE, app.files.scanInput("normal.txt") { "PK harmless family notes".byteInputStream() }.verdict.level)
        val apk = app.files.scanInput("ordinary.zip") { testAssets.open("dummy-risky.apk") }
        assertEquals(Level.DANGEROUS, apk.verdict.level)
        assertTrue(ReasonCode.RISKY_PERMISSIONS in apk.verdict.reasonCodes)
        assertFalse(ReasonCode.SUSPICIOUS_FILENAME in apk.verdict.reasonCodes)
        assertEquals(Level.SUSPICIOUS, app.files.scanInput("broken.apk") { "not an archive".byteInputStream() }.verdict.level)
        assertTrue(File(app.cacheDir, "file-inspection").listFiles().orEmpty().isEmpty())
    }

    @Test fun sharedApkOpensAFileWarningWithoutInstallingIt() {
        val folder = File(app.cacheDir, "test-fixtures").apply { mkdirs() }
        val file = File(folder, "private_fixture_name.apk")
        testAssets.open("dummy-risky.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
        try {
            val uri = FileProvider.getUriForFile(app, "org.suraksha.kavach.testfiles", file)
            compose.runOnIdle {
                compose.activity.startActivity(Intent(compose.activity, ShareFileActivity::class.java)
                    .setAction(Intent.ACTION_SEND).setType("application/vnd.android.package-archive")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            // The share result briefly contains the same title while its activity
            // hands off to the alert. Wait for the actual foreground warning first.
            compose.waitForFocusedAlert()
            compose.waitUntil(20_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.file_danger_title)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.file_danger_title)).assertIsDisplayed()
            compose.onNodeWithText("private_fixture_name.apk").assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.understand)).performClick()
            compose.waitUntil(10_000) { app.history.value.any { ReasonCode.FILE_SCAN in it.toResult().verdict.reasonCodes } }
            assertTrue(app.history.value.filter { ReasonCode.FILE_SCAN in it.toResult().verdict.reasonCodes }.all { it.domain.isEmpty() })
        } finally { file.delete() }
    }

    @Test fun familyCodeCanBeSetAndCheckedWithoutRevealingIt() {
        compose.onNodeWithText(compose.activity.getString(R.string.settings)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.family_code_title)).performScrollTo().performClick()
        compose.onNodeWithTag("familyCode").performTextInput("Blue Mango")
        compose.onNodeWithTag("familyCodeConfirm").performTextInput("Blue Mango")
        compose.onNodeWithText(compose.activity.getString(R.string.code_save)).performScrollTo().performClick()
        try {
            compose.waitUntil(20_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.code_saved)).fetchSemanticsNodes().isNotEmpty() }
            assertFalse(app.getSharedPreferences("sealed_secrets", 0).all.toString().contains("Blue Mango"))
            compose.onNodeWithText(compose.activity.getString(R.string.home)).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.call_button)).performScrollTo().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.ask_code)).assertIsDisplayed()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("codeAnswer").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("codeAnswer").performScrollTo().performTextInput("blue mango")
            compose.onNodeWithText(compose.activity.getString(R.string.check_code)).performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.code_match)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.code_match)).performScrollTo().assertIsDisplayed()
        } finally { app.secrets.remove("family_code_hash_v1") }
    }
}
