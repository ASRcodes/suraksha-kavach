package org.suraksha.kavach

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.lang.ref.WeakReference
import org.suraksha.kavach.audio.WarningAudio
import org.suraksha.kavach.family.*
import org.suraksha.kavach.files.*
import org.suraksha.kavach.notifications.WarningOverlay
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

open class KavachApplication : Application() {
    open val secureWindows: Boolean = true
    protected open fun createSmsSender(): SmsSender = AndroidSmsSender(this)
    private var foreground: WeakReference<Activity>? = null
    lateinit var files: FileScanner; private set
    lateinit var watcher: FolderWatcher; private set
    lateinit var familyCode: FamilyCode; private set
    lateinit var guardian: GuardianManager; private set
    lateinit var audio: WarningAudio; private set
    lateinit var overlays: WarningOverlay; private set
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
        files = FileScanner(this)
        familyCode = FamilyCode(secrets)
        watcher = FolderWatcher(this)
        scope.launch(Dispatchers.IO) { watcher.refresh() }
        guardian = GuardianManager(this, secrets, createSmsSender())
        audio = WarningAudio(this)
        overlays = WarningOverlay(this)
        scope.launch(Dispatchers.IO) { guardian.refresh() }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { foreground = WeakReference(activity); scope.launch(Dispatchers.IO) { guardian.refresh() } }
            override fun onActivityPaused(activity: Activity) {
                if (foreground?.get() === activity) foreground = null
                if (activity is MainActivity) audio.stop()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        store = ModelStore(this)
        alerts = AlertDispatcher(this)
        alerts.createChannels()
        retryModels()
        loadedDatabase = scope.async(Dispatchers.IO) {
            try {
                HistoryDatabase.open(this@KavachApplication, secrets).also { storageState.value = LoadState.READY }
            } catch (e: CancellationException) { throw e }
            catch (_: LinkageError) { storageState.value = LoadState.FAILED; null }
            catch (_: Exception) { storageState.value = LoadState.FAILED; null }
        }
        scope.launch {
            loadedDatabase.await()?.let { db ->
                try {
                    pruneHistory()
                    db.history().observe().collect { history.value = it }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { storageState.value = LoadState.FAILED }
            }
        }
        RetentionJob.schedule(this)
    }

    fun retryModels() {
        if (::loadedModels.isInitialized && loadedModels.isActive) return
        modelState.value = LoadState.LOADING
        loadedModels = scope.async(Dispatchers.IO) {
            try {
                pipeline = MessagePipeline(store.load())
                modelState.value = LoadState.READY
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { modelState.value = LoadState.FAILED }
        }
    }

    suspend fun processMessage(text: String, source: String, simulate: Boolean = false): MessageOutcome {
        if (!simulate && !settings.state.value.protectionEnabled) return MessageOutcome()
        loadedModels.await()
        if (!simulate && !settings.state.value.protectionEnabled) return MessageOutcome()
        val selected = if (BuildConfig.DEBUG && demoEnabled.value) demo else pipeline
        if (selected == null) return MessageOutcome(available = false)
        val results = try { withContext(Dispatchers.Default) { selected.scan(text, source, deduplicate = !simulate) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { return MessageOutcome(available = false) }
        if (!simulate && !settings.state.value.protectionEnabled) return MessageOutcome()
        results.filter { it.verdict.level != Level.SAFE }.maxByOrNull { it.verdict.level.ordinal }?.let {
            handleWarning(it, manual = simulate)
        }
        saveResults(results)
        return MessageOutcome(results)
    }

    suspend fun processFile(uri: Uri, manual: Boolean = true): ScanResult {
        val result = files.scan(uri)
        if (manual || settings.state.value.protectionEnabled) {
            if (result.verdict.level != Level.SAFE) handleWarning(result, manual)
            saveResults(listOf(result))
        }
        return result
    }

    private suspend fun handleWarning(result: ScanResult, manual: Boolean) {
        scope.launch(Dispatchers.IO) {
            try { guardian.onDanger(result) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { guardian.markFailure() }
        }
        if (manual) {
            if (result.verdict.level == Level.SUSPICIOUS) alerts.post(result, false)
            return
        }
        withContext(Dispatchers.Main.immediate) {
            if (result.verdict.level == Level.DANGEROUS) {
                val activity = foreground?.get()
                if (activity != null && !activity.isFinishing) {
                    runCatching { activity.startActivity(AlertActivity.intent(activity, result)) }.onFailure { alerts.post(result) }
                }
                else {
                    val overlayVisible = overlays.show(result)
                    alerts.post(result, allowFullScreen = !overlayVisible)
                }
            } else alerts.post(result, false)
        }
    }

    fun dismissWarnings() {
        scope.launch(Dispatchers.Main.immediate) { audio.stop(); overlays.dismiss(); alerts.cancel() }
    }
    fun speakWarning() = playWarning(false)
    fun previewWarning() = playWarning(true)
    private fun playWarning(explicit: Boolean) {
        if (explicit || settings.state.value.voiceEnabled) scope.launch(Dispatchers.Main.immediate) { audio.play(settings.state.value.language) }
    }
    fun callGuardian(context: Context) {
        scope.launch(Dispatchers.IO) {
            val number = runCatching { guardian.number() }.getOrNull()
            withContext(Dispatchers.Main) {
                val destination = if (number != null) Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                    else Intent(context, MainActivity::class.java).putExtra("page", "GUARDIAN")
                destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(destination) }.onFailure {
                    Toast.makeText(context, AppSettings.localized(context).getString(R.string.dial_unavailable), Toast.LENGTH_LONG).show()
                }
            }
        }
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
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { storageState.value = LoadState.FAILED }
        }
    }

    private fun cutoff() = System.currentTimeMillis() - settings.state.value.retentionDays * 86_400_000L
    suspend fun pruneHistory() {
        val db = loadedDatabase.await() ?: return
        try { persistence.withLock { db.history().deleteBefore(cutoff()); db.history().trim() } }
        catch (e: CancellationException) { throw e }
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
                } catch (e: CancellationException) { refreshState.value = RefreshState.IDLE; throw e }
                catch (_: Exception) { refreshState.value = RefreshState.FAILED }
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
