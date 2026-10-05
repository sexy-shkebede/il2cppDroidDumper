package dev.il2cpp.dumper.app.work

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.il2cpp.dumper.app.DumperApp
import dev.il2cpp.dumper.app.MainActivity
import dev.il2cpp.dumper.app.data.DumpEngine
import dev.il2cpp.dumper.app.data.DumpInput
import dev.il2cpp.dumper.app.data.DumpStore
import dev.il2cpp.dumper.app.data.DumpUi
import dev.il2cpp.dumper.app.data.LogLine
import dev.il2cpp.dumper.core.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the dump as a foreground service so it survives the Activity being backgrounded and large
 * games don't get killed mid-dump. State is published through [DumpController].
 */
class DumpService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val (input, config) = DumpController.pending ?: run {
            stopSelf(); return START_NOT_STICKY
        }
        DumpController.pending = null
        startForegroundCompat(notification("Starting…", 0f, true))
        val log = ArrayList<LogLine>()
        val app = applicationContext as DumperApp
        val engine = DumpEngine(applicationContext, app.store)
        val title = titleOf(input)

        job = scope.launch {
            val ui = object : DumpUi {
                override fun stage(text: String, fraction: Float) {
                    DumpController.setRunning(title, text, fraction, log.toList())
                    updateNotification(text, fraction)
                }

                override fun log(line: LogLine) {
                    synchronized(log) { log.add(line) }
                    DumpController.setRunning(title, (DumpController.state.value as? DumpController.State.Running)?.stage ?: "", (DumpController.state.value as? DumpController.State.Running)?.fraction ?: -1f, log.toList())
                }

                override fun isCancelled(): Boolean = DumpController.cancelRequested
            }
            DumpController.setRunning(title, "Preparing", -1f, emptyList())
            try {
                val record = engine.run(input, config, ui)
                DumpController.setDone(record)
                notifyDone(record.id, title, true, "${record.typeCount} types")
            } catch (e: kotlinx.coroutines.CancellationException) {
                DumpController.setFailed(title, "Cancelled", log.toList())
            } catch (e: java.util.concurrent.CancellationException) {
                DumpController.setFailed(title, "Cancelled", log.toList())
            } catch (e: Throwable) {
                DumpController.setFailed(title, e.message ?: e.toString(), log.toList())
                notifyDone("", title, false, e.message ?: "Failed")
            } finally {
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun titleOf(input: DumpInput): String = when (input) {
        is DumpInput.App -> input.app.label
        is DumpInput.Files -> input.title.ifEmpty { input.binary.name }
        is DumpInput.Content -> "Dump"
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
    }

    private fun updateNotification(text: String, fraction: Float) {
        androidx.core.app.NotificationManagerCompat.from(this).let { nm ->
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            nm.notify(NOTIF_ID, notification(text, fraction, fraction < 0f))
        }
    }

    private fun notification(text: String, fraction: Float, indeterminate: Boolean): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, DumperApp.CHANNEL_ID)
            .setContentTitle("Dumping…")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, (fraction * 100).toInt().coerceIn(0, 100), indeterminate)
            .setContentIntent(intent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notifyDone(recordId: String, title: String, ok: Boolean, detail: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val intent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("openDump", recordId),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, DumperApp.CHANNEL_ID)
            .setContentTitle(if (ok) "Dump complete: $title" else "Dump failed: $title")
            .setContentText(detail)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF_ID + 1, n)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val NOTIF_ID = 1001

        fun start(context: Context, input: DumpInput, config: Config) {
            DumpController.requestStart(input, config)
            val intent = Intent(context, DumpService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
