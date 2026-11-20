package org.suraksha.kavach

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import org.suraksha.kavach.family.SmsSender
import java.util.concurrent.CopyOnWriteArrayList

/** Instrumentation can never send carrier SMS; secure production windows stay enabled. */
class KavachTestApplication : KavachApplication() {
    override val secureWindows = false
    val sms = CopyOnWriteArrayList<Pair<String, String>>()
    override fun createSmsSender() = SmsSender { destination, message, _ -> sms += destination to message }
}
class KavachTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, KavachTestApplication::class.java.name, context)
}
