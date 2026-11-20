package org.suraksha.kavach

import android.content.Context
import androidx.activity.ComponentActivity
import org.suraksha.kavach.data.AppSettings

open class LocalizedActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppSettings.localized(newBase)) }
}
