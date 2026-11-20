package org.suraksha.kavach

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.suraksha.core.*
import org.suraksha.kavach.data.*
import org.suraksha.kavach.notifications.AlertDispatcher

enum class LoadState { LOADING, READY, FAILED }
enum class RefreshState { IDLE, WORKING, DONE, FAILED }
data class MessageOutcome(val results: List<ScanResult> = emptyList(), val available: Boolean = true)

class KavachApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val demoEnabled = MutableStateFlow(false)
    val listenerConnected = MutableStateFlow(false)
    val modelState = MutableStateFlow(LoadState.LOADING)
    val storageState = MutableStateFlow(LoadState.LOADING)
    val history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val refreshState = MutableStateFlow(RefreshState.IDLE)
    lateinit var settings: AppSettings; private set
    lateinit var secrets: SecretStore; private set
    lateinit var alerts: AlertDispatcher; private set
    private lateinit var store: ModelStore
    private lateinit var loadedModels: Deferred<Unit>
    private lateinit var loadedDatabase: Deferred<HistoryDatabase?>
    @Volatile private var pipeline: MessagePipeline? = null
    private val demo = MessagePipeline(DemoScorer())
    private val persistence = Mutex()
    private val refreshLock = Mutex()

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        secrets = SecretStore(this)
        store = ModelStore(this)
        alerts = AlertDispatcher(this)
        alerts.createChannels()
        retryModels()
        loadedDatabase = scope.async(Dispatchers.IO) {
            try {
                HistoryDatabase.open(this@KavachApplication, secrets).also { storageState.value = LoadState.READY }
            } catch (_: Exception) { storageState.value = LoadState.FAILED; null }
        }
        scope.launch {
            loadedDatabase.await()?.let { db ->
                try {
                    pruneHistory()
                    db.history().observe().collect { history.value = it }
                } catch (_: Exception) { storageState.value = LoadState.FAILED }
            }
        }
        RetentionJob.schedule(this)
    }

    fun retryModels() {
        modelState.value = LoadState.LOADING
        loadedModels = scope.async(Dispatchers.IO) {
            try {
                pipeline = MessagePipeline(store.load())
                modelState.value = LoadState.READY
            } catch (_: Exception) { modelState.value = LoadState.FAILED }
        }
    }

    suspend fun processMessage(text: String, source: String, simulate: Boolean = false): MessageOutcome {
        if (!simulate && !settings.state.value.protectionEnabled) return MessageOutcome()
        loadedModels.await()
        val selected = if (BuildConfig.DEBUG && demoEnabled.value) demo else pipeline
        if (selected == null) return MessageOutcome(available = false)
        val results = try { withContext(Dispatchers.Default) { selected.scan(text, source, deduplicate = !simulate) } }
            catch (_: Exception) { return MessageOutcome(available = false) }
        results.filter { it.verdict.level != Level.SAFE }.maxByOrNull { it.verdict.level.ordinal }?.let {
            alerts.post(it, allowFullScreen = !simulate)
        }
        saveResults(results)
        return MessageOutcome(results)
    }

    fun saveResults(results: List<ScanResult>) {
        if (results.isEmpty()) return
        // Only these metadata objects survive asynchronous dispatch.
        val entries = results.groupBy { it.domain }.values.map { group ->
            HistoryEntry.from(group.maxBy { it.verdict.level.ordinal })
        }
        scope.launch(Dispatchers.IO) {
            val db = loadedDatabase.await() ?: return@launch
            try {
                persistence.withLock {
                    db.history().insert(entries)
                    db.history().deleteBefore(cutoff())
                    db.history().trim()
                }
            } catch (_: Exception) { storageState.value = LoadState.FAILED }
        }
    }

    private fun cutoff() = System.currentTimeMillis() - settings.state.value.retentionDays * 86_400_000L
    suspend fun pruneHistory() {
        val db = loadedDatabase.await() ?: return
        try { persistence.withLock { db.history().deleteBefore(cutoff()); db.history().trim() } }
        catch (_: Exception) { storageState.value = LoadState.FAILED }
    }
    internal suspend fun databaseForTests(): HistoryDatabase? = loadedDatabase.await()

    fun refreshBlocklist() {
        if (!settings.state.value.enhanced || refreshState.value == RefreshState.WORKING) return
        refreshState.value = RefreshState.WORKING
        scope.launch(Dispatchers.IO) {
            refreshLock.withLock {
                try {
                    store.refresh { settings.state.value.enhanced }
                    val updated = MessagePipeline(store.load())
                    pipeline = updated
                    modelState.value = LoadState.READY
                    refreshState.value = RefreshState.DONE
                } catch (_: Exception) { refreshState.value = RefreshState.FAILED }
            }
        }
    }

    fun setDemo(enabled: Boolean) {
        demoEnabled.value = BuildConfig.DEBUG && enabled
        demo.clear()
        pipeline?.clear()
        alerts.cancel()
    }
}
