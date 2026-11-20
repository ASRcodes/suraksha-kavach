package org.suraksha.core

import org.junit.Assert.*
import org.junit.Test

class FileAndPassphraseTest {
    private val harmlessHash = "0".repeat(64)
    private val eicarHash = "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f"
    private val rules = FileRules(mapOf(eicarHash to HashKind.EICAR_TEST))

    @Test fun hashesAreCheckedIndependentlyOfNameAndRecognizeTheHarmlessTest() {
        val result = rules.evaluate(eicarHash.uppercase(), "holiday.txt")
        assertEquals(Level.DANGEROUS, result.level)
        assertTrue(ReasonCode.EICAR_TEST in result.reasonCodes)
        assertEquals(Level.SAFE, rules.evaluate(harmlessHash, "eicar.txt").level)
    }
    @Test fun permissionCombinationIsDangerousEvenAfterRenaming() {
        val apk = ApkSignals(setOf("android.permission.READ_SMS", "android.permission.SYSTEM_ALERT_WINDOW"))
        assertEquals(Level.DANGEROUS, rules.evaluate(harmlessHash, "ordinary.apk", apk).level)
        assertEquals(Level.SUSPICIOUS, rules.evaluate(harmlessHash, "ordinary.apk", ApkSignals(setOf("android.permission.SEND_SMS"))).level)
    }
    @Test fun declaredComponentsAndLuresProduceExplainableWarnings() {
        assertEquals(Level.DANGEROUS, rules.evaluate(harmlessHash, "app.apk",
            ApkSignals(setOf("android.permission.REQUEST_INSTALL_PACKAGES"), accessibilityService = true)).level)
        val lure = rules.evaluate(harmlessHash, "PM_KISAN.apk", ApkSignals(emptySet()))
        assertEquals(Level.SUSPICIOUS, lure.level)
        assertTrue(ReasonCode.SUSPICIOUS_FILENAME in lure.reasonCodes)
        assertEquals(Level.SAFE, rules.evaluate(harmlessHash, "challan.pdf").level)
    }
    @Test fun unreadableApksAndFilesNeverReceiveASafeVerdict() {
        assertEquals(Level.SUSPICIOUS, rules.evaluate(harmlessHash, "broken.apk", apkExpected = true).level)
        assertEquals(Level.SUSPICIOUS, rules.evaluate("", "", readable = false).level)
    }
    @Test fun codeWordsUseRandomSaltsAndRejectTheWrongAnswer() {
        val first = PassphraseHash.create("Blue Mango")
        val second = PassphraseHash.create("Blue Mango")
        assertNotEquals(first, second)
        assertFalse(first.contains("Mango"))
        assertTrue(PassphraseHash.matches("  BLUE   mango ", first))
        assertFalse(PassphraseHash.matches("blue banana", first))
        assertFalse(PassphraseHash.matches("Blue Mango", first.replace(":210000:", ":999999999:")))
    }
    @Test fun unicodeWordsWorkAndInvalidRecordsFailClosed() {
        val word = "à¨¸à¨¾à¨¡à¨¾ à¨ªà¨°à¨¿à¨µà¨¾à¨°"
        assertTrue(PassphraseHash.matches(word, PassphraseHash.create(word)))
        assertFalse(PassphraseHash.valid("abc"))
        assertFalse(PassphraseHash.valid("x".repeat(300)))
        assertFalse(PassphraseHash.matches("valid word", "v1:210000:bad:bad"))
        assertTrue(ReasonCode.entries.all { ReasonMessages.get(it, "hi").isNotBlank() && ReasonMessages.get(it, "pa").isNotBlank() })
    }
}
