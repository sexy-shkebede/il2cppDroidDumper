package dev.il2cpp.dumper.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dev.il2cpp.dumper.app.data.DumpStore
import dev.il2cpp.dumper.app.data.InstalledAppsRepository
import dev.il2cpp.dumper.app.data.SettingsRepository

class DumperApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var store: DumpStore
        private set
    lateinit var apps: InstalledAppsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        store = DumpStore(this)
        apps = InstalledAppsRepository(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Dumping", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while a dump is running"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "dump_progress"
        lateinit var instance: DumperApp
            private set
    }
}
