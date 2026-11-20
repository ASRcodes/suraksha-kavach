package org.suraksha.kavach.files

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.suraksha.core.*
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

/** Streams hashes, inspects APK metadata, and never installs or executes the archive. */
class FileScanner(private val context: Context) {
    private val lock = Mutex()
    private val directory = File(context.cacheDir, "file-inspection")
    private val rules by lazy {
        val bytes = context.assets.open("bad_hashes.txt").use { it.readBytes() }
        val manifest = JSONObject(context.assets.open("asset_manifest.json").bufferedReader().use { it.readText() }).getJSONObject("files")
        check(hex(MessageDigest.getInstance("SHA-256").digest(bytes)) == manifest.getString("bad_hashes.txt"))
        FileRules(bytes.toString(Charsets.UTF_8).lineSequence().map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith('#') }.associate {
                val parts = it.split(Regex("\\s+"))
                require(parts.size == 2)
                parts[0] to HashKind.valueOf(parts[1])
            })
    }

    suspend fun scan(uri: Uri): ScanResult = withContext(Dispatchers.IO) {
        if (uri.scheme != "content") return@withContext unreadable()
        try {
            var name = ""
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0).orEmpty().take(256)
                    if (!it.isNull(1) && it.getLong(1) > MAX_BYTES) return@withContext unreadable()
                }
            }
            scanInput(name, context.contentResolver.getType(uri).orEmpty()) {
                requireNotNull(context.contentResolver.openInputStream(uri))
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { unreadable() }
    }

    internal suspend fun scanInput(name: String, mime: String = "", open: () -> InputStream): ScanResult =
        withContext(Dispatchers.IO) {
            lock.withLock {
                var temporary: File? = null
                try {
                    directory.mkdirs()
                    // Only our temporary archives live here; clean leftovers after process death.
                    directory.listFiles()?.filter { it.isFile && it.name.startsWith("scan-") }?.forEach { it.delete() }
                    val expected = name.lowercase(Locale.ROOT).endsWith(".apk") || mime == "application/vnd.android.package-archive"
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    var archive = expected
                    val deadline = android.os.SystemClock.elapsedRealtime() + 120_000
                    open().buffered().use { input ->
                        input.mark(4)
                        val signature = ByteArray(4)
                        val header = input.read(signature)
                        input.reset()
                        archive = archive || (header == 4 && signature[0] == 80.toByte() && signature[1] == 75.toByte() && signature[2] in byteArrayOf(3, 5, 7) && signature[3] in byteArrayOf(4, 6, 8))
                        if (archive) temporary = File.createTempFile("scan-", ".apk", directory)
                        val output = temporary?.outputStream()?.buffered()
                        try {
                            val buffer = ByteArray(32_768)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                check(android.os.SystemClock.elapsedRealtime() <= deadline)
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= MAX_BYTES)
                                digest.update(buffer, 0, count)
                                output?.write(buffer, 0, count)
                            }
                        } finally { output?.close() }
                    }
                    val hash = hex(digest.digest())
                    val signatureVerdict = rules.evaluate(hash, name)
                    if (signatureVerdict.level == Level.DANGEROUS)
                        return@withLock ScanResult("", signatureVerdict, System.currentTimeMillis())
                    var apk: ApkSignals? = null
                    var isApk = expected
                    temporary?.let { file ->
                        ZipFile(file).use { zip ->
                            zip.getEntry("AndroidManifest.xml")?.let { manifest ->
                                isApk = true
                                require(manifest.size in 1..1_048_576L)
                            }
                        }
                        if (isApk) apk = inspectApk(file)
                    }
                    ScanResult("", rules.evaluate(hash, name, apk, isApk), System.currentTimeMillis())
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { unreadable() }
                finally { temporary?.delete() }
            }
        }

    @Suppress("DEPRECATION")
    private fun inspectApk(file: File): ApkSignals? {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_META_DATA
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        return ApkSignals(info.requestedPermissions.orEmpty().toSet(),
            info.services.orEmpty().any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" || it.metaData?.containsKey("android.accessibilityservice") == true },
            info.receivers.orEmpty().any { it.permission == "android.permission.BIND_DEVICE_ADMIN" || it.metaData?.containsKey("android.app.device_admin") == true })
    }
    companion object {
        const val MAX_BYTES = 100L * 1024 * 1024
        fun unreadable() = ScanResult("", Verdict(Level.SUSPICIOUS, 0.5, setOf(ReasonCode.FILE_SCAN, ReasonCode.UNREADABLE_FILE)), System.currentTimeMillis())
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
