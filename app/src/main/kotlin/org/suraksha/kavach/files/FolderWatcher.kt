package org.suraksha.kavach.files

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong
import org.suraksha.kavach.KavachApplication

enum class WatchState { OFF, READY, SCANNING, NEEDS_ACCESS }
class FolderWatcher(private val app: KavachApplication) {
    val state = MutableStateFlow(WatchState.OFF)
    private val scanning = Mutex()
    private val generation = AtomicLong()
    private val prefs = app.getSharedPreferences("folder_watch", Context.MODE_PRIVATE)
    private val scheduler = app.getSystemService(JobScheduler::class.java)
    private fun folder(): Uri? = app.secrets.read("watched_folder")?.let {
        try { it.toString(Charsets.UTF_8).toUri() } finally { it.fill(0) }
    }
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val uri = runCatching { folder() }.getOrNull()
        state.value = when {
            uri == null -> WatchState.OFF
            app.contentResolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission } -> WatchState.NEEDS_ACCESS
            else -> WatchState.READY
        }
        if (state.value == WatchState.READY) schedule()
    }
    suspend fun select(uri: Uri) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" && DocumentsContract.isTreeUri(uri))
        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val previous = runCatching { folder() }.getOrNull()
        generation.incrementAndGet()
        app.secrets.write("watched_folder", uri.toString().toByteArray())
        prefs.edit { putLong("after", System.currentTimeMillis()); putInt("at_count", 0) }
        if (previous != null && previous != uri) runCatching {
            app.contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        refresh()
    }
    suspend fun disable() = withContext(Dispatchers.IO) {
        generation.incrementAndGet()
        scheduler.cancel(JOB_ID)
        val previous = runCatching { folder() }.getOrNull()
        app.secrets.remove("watched_folder")
        prefs.edit { clear() }
        if (previous != null) runCatching { app.contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        state.value = WatchState.OFF
    }
    private fun schedule() {
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(app, FolderWatchJob::class.java))
            .setPeriodic(15 * 60_000L).setRequiresBatteryNotLow(true).setPersisted(true).build())
    }
    suspend fun check() = withContext(Dispatchers.IO) {
        if (!app.settings.state.value.protectionEnabled) return@withContext
        val uri = runCatching { folder() }.getOrNull() ?: return@withContext
        val started = System.currentTimeMillis()
        val after = prefs.getLong("after", started)
        val consumedAtBoundary = prefs.getInt("at_count", 0)
        val token = generation.get()
        if (!scanning.tryLock()) return@withContext
        state.value = WatchState.SCANNING
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val candidates = mutableListOf<Pair<Uri, Long>>()
            app.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                var inspected = 0
                while (cursor.moveToNext()) {
                    require(inspected++ < 1000)
                    currentCoroutineContext().ensureActive()
                    val modified = cursor.getLong(2)
                    if (cursor.getString(1) != DocumentsContract.Document.MIME_TYPE_DIR && modified >= after && modified > 0 && modified <= started) {
                        candidates += DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)) to modified
                    }
                }
            } ?: error("Folder access unavailable")
            val pending = candidates.sortedWith(compareBy<Pair<Uri, Long>> { it.second }.thenBy { it.first.toString() })
                .filterIndexed { index, item -> item.second != after || index >= consumedAtBoundary }
            val batch = pending.take(20)
            for ((document, _) in batch) {
                currentCoroutineContext().ensureActive()
                if (!app.settings.state.value.protectionEnabled || token != generation.get()) {
                    state.value = if (folder() == null) WatchState.OFF else WatchState.READY
                    return@withContext
                }
                app.processFile(document, manual = false)
            }
            // A bounded, non-recursive job. No document names, hashes or bodies are saved.
            if (token == generation.get()) {
                val checkpoint = batch.lastOrNull()?.second ?: started
                val countAtCheckpoint = batch.count { it.second == checkpoint } + if (checkpoint == after) consumedAtBoundary else 0
                prefs.edit { putLong("after", checkpoint); putInt("at_count", countAtCheckpoint) }
                state.value = WatchState.READY
            }
        } catch (e: CancellationException) { if (token == generation.get()) state.value = WatchState.READY; throw e }
        catch (_: Exception) { if (token == generation.get()) state.value = WatchState.NEEDS_ACCESS }
        finally { scanning.unlock() }
    }
    companion object { private const val JOB_ID = 811 }
}
class FolderWatchJob : JobService() {
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as KavachApplication
        work = app.scope.launch { app.watcher.check(); jobFinished(params, false) }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
}
