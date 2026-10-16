package yt.ivy.librecanteen.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import yt.ivy.librecanteen.LibreCanteenApp
import yt.ivy.librecanteen.MainActivity
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.data.ReminderPolicy
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "ordering"
private const val WORK_NAME = "order-reminder"
private const val NOTIFICATION_ID = 1

object Reminders {
    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LibreCanteenApp
        val today = LocalDate.now()
        if (!app.settings.reminders.value || !ReminderPolicy.isReminderDay(today)) return Result.success()

        val monday = ReminderPolicy.nextMonday(today)
        if (app.settings.lastReminderWeek == monday.toString()) return Result.success()

        val week = try {
            (0L..4L).map { app.repository.menu(monday.plusDays(it)) }
        } catch (e: CanteenException.Network) {
            return Result.retry()
        } catch (e: CanteenException) {
            return Result.success()
        }

        if (ReminderPolicy.needsReminder(week) && notify()) {
            app.settings.lastReminderWeek = monday.toString()
        }
        return Result.success()
    }

    private fun notify(): Boolean {
        val context = applicationContext
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_ordering), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }
}
