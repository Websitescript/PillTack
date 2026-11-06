package com.pilltack.app

import android.app.Application
import androidx.room.Room
import com.pilltack.app.data.AppDb
import com.pilltack.app.data.Repo
import com.pilltack.app.notify.Reminders

class PillApp : Application() {
    lateinit var repo: Repo
    override fun onCreate() {
        super.onCreate()
        repo = Repo(this, Room.databaseBuilder(this, AppDb::class.java, "pilltack.db").build())
        Reminders.channels(this)
    }
}
