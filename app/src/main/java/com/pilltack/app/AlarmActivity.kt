package com.pilltack.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.pilltack.app.ui.AlarmScreen
import com.pilltack.app.ui.PillTrackTheme

/** Call-style full-screen reminder. Shown over the lock screen by the notification's full-screen intent. */
class AlarmActivity : ComponentActivity() {
    private var doseId by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        doseId = intent.getLongExtra("dose", 0L)
        val repo = (application as PillApp).repo
        setContent { PillTrackTheme { AlarmScreen(repo, doseId) { finish() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        doseId = intent.getLongExtra("dose", 0L)
    }
}
