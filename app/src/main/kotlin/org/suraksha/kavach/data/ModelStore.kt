package org.suraksha.kavach.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import org.suraksha.core.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The only network operation in the app: an explicitly requested, download-only domain-list update. */
class ModelStore(private val context: Context) {
    private val downloaded = AtomicFile(File(context.filesDir, "downloaded_domains.txt"))
    private fun asset(name: String) = context.assets.open(name).use { it.readBytes() }

    fun load(): VerdictEngine {
        val manifest = JSONObject(asset("asset_manifest.json").toString(Charsets.UTF_8)).getJSONObject("files")
        val checked = mutableMapOf<String, String>()
        for (name in listOf("url_model.json", "text_model.json", "public_suffix_list.dat", "top_domains.txt", "blocklist.txt")) {
            val bytes = asset(name)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(digest == manifest.getString(name)) { "Bundled model integrity check failed" }
            checked[name] = bytes.toString(Charsets.UTF_8)
        }
        val extra = runCatching { downloaded.openRead().use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        if (extra != null) runCatching { validate(extra) }.getOrNull()?.let {
            checked["blocklist.txt"] = checked.getValue("blocklist.txt") + "\n" + it
        }
        return VerdictEngine.load { checked.getValue(it) }
    }

    private fun validate(text: String): String {
        val suffixes = PublicSuffixList(asset("public_suffix_list.dat").toString(Charsets.UTF_8))
        val trusted = Allowlist(asset("top_domains.txt").toString(Charsets.UTF_8), suffixes)
        val accepted = linkedSetOf<String>()
        require(text.length <= MAX_BYTES)
        for (line in text.lineSequence()) {
            val host = line.trim()
            if (host.isBlank() || host.startsWith('#')) continue
            val parsed = UrlParser.parse("https://$host")
            require(parsed != null && parsed.host == host && host.length <= 253)
            require(parsed.path.isEmpty() || parsed.path == "/")
            require(!host.contains('/') && !host.contains('@') && !host.contains(':') && !host.contains('?') && !host.contains('#'))
            if (suffixes.registrableDomain(host) != null && !trusted.contains(host)) accepted += host
            require(accepted.size <= 200_000)
        }
        require(accepted.isNotEmpty())
        return accepted.joinToString("\n")
    }

    fun refresh(stillEnabled: () -> Boolean) {
        check(stillEnabled())
        val connection = URL(FEED).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        try {
            check(connection.responseCode == 200)
            require(connection.contentLengthLong <= MAX_BYTES || connection.contentLengthLong < 0)
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16_384)
                while (true) {
                    check(stillEnabled())
                    val read = input.read(buffer)
                    if (read == -1) break
                    require(output.size() + read <= MAX_BYTES)
                    output.write(buffer, 0, read)
                }
            }
            val safe = validate(output.toString(Charsets.UTF_8.name()))
            check(stillEnabled())
            val stream = downloaded.startWrite()
            try { stream.write(safe.toByteArray()); downloaded.finishWrite(stream) }
            catch (error: Exception) { downloaded.failWrite(stream); throw error }
        } finally { connection.disconnect() }
    }

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024
        private const val FEED = "https://raw.githubusercontent.com/ASRcodes/suraksha-kavach/main/app/src/main/assets/blocklist.txt"
    }
}
