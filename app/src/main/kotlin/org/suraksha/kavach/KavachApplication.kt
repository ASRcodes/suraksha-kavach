package org.suraksha.kavach

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.suraksha.core.DemoScorer
import org.suraksha.core.MessagePipeline
import org.suraksha.core.ScanResult
import org.suraksha.kavach.notifications.AlertDispatcher

class KavachApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val demoEnabled = MutableStateFlow(false)
    val listenerConnected = MutableStateFlow(false)
    private val pipeline = MessagePipeline(DemoScorer())
    lateinit var alerts: AlertDispatcher
        private set

    override fun onCreate() {
        super.onCreate()
        alerts = AlertDispatcher(this)
        alerts.createChannels()
    }

    fun processMessage(text: String, source: String, simulate: Boolean = false): List<ScanResult> {
        if (!BuildConfig.DEBUG || !demoEnabled.value) return emptyList()
        return pipeline.scan(text, source, deduplicate = !simulate).also { results ->
            results.maxByOrNull { it.verdict.level.ordinal }?.let(alerts::post)
        }
    }

    fun setDemo(enabled: Boolean) {
        demoEnabled.value = BuildConfig.DEBUG && enabled
        pipeline.clear()
        if (!enabled) alerts.cancel()
    }
}
