package org.suraksha.kavach.notifications

import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.suraksha.core.ScanResult
import org.suraksha.kavach.KavachApplication
import org.suraksha.kavach.WarningScreen
import org.suraksha.kavach.data.AppSettings
import org.suraksha.kavach.ui.KavachTheme

/** A user-authorized warning surface. It never reads taps, content or passwords from other apps. */
class WarningOverlay(private val app: KavachApplication) {
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private val current = mutableStateOf<ScanResult?>(null)
    private val manager = app.getSystemService(WindowManager::class.java)

    fun show(result: ScanResult): Boolean {
        if (!app.settings.state.value.overlayEnabled || !Settings.canDrawOverlays(app) ||
            app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        current.value = result
        if (view != null) return true
        val context = AppSettings.localized(app)
        val lifecycle = OverlayOwner()
        val surface = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            isFocusableInTouchMode = true
            setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { app.dismissWarnings(); true } else false
            }
            setContent {
                KavachTheme {
                    current.value?.let { warning ->
                        WarningScreen(warning, context.resources.configuration.locales[0].language,
                            onUnderstand = app::dismissWarnings,
                            onGuardian = { app.dismissWarnings(); app.callGuardian(app) },
                            onReplay = { app.alerts.cancel(); app.previewWarning() })
                    }
                }
            }
        }
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            if (app.secureWindows) WindowManager.LayoutParams.FLAG_SECURE else 0
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.OPAQUE)
        return try {
            owner = lifecycle
            view = surface
            manager.addView(surface, params)
            lifecycle.registry.currentState = Lifecycle.State.RESUMED
            surface.requestFocus()
            true
        } catch (_: Exception) { dismiss(); false }
    }

    fun dismiss() {
        view?.let { runCatching { manager.removeViewImmediate(it) }; it.disposeComposition() }
        view = null
        owner?.registry?.currentState = Lifecycle.State.DESTROYED
        owner = null
        current.value = null
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        init { saved.performAttach(); saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
    }
}
