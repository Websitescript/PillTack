package com.pilltack.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pilltack.app.MainActivity
import com.pilltack.app.PillApp
import com.pilltack.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 2x1 home-screen widget: shows the next medicine, its date and time. Tap to open the app. */
class NextDoseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { (ctx.applicationContext as PillApp).repo.updateWidget() } finally { pending.finish() }
        }
    }

    companion object {
        fun views(ctx: Context, name: String, whenText: String): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_next)
            rv.setTextViewText(R.id.w_name, name)
            rv.setTextViewText(R.id.w_when, whenText)
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.w_root, open)
            return rv
        }
    }
}
