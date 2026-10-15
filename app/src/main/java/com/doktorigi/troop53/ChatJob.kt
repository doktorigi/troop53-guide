package com.doktorigi.troop53

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Checks for new troop chat messages about every 15 minutes and posts a notification.
 * ponytail: polling, because instant push needs Cloud Functions (paid Blaze plan). Swap for FCM if the troop upgrades.
 */
class ChatJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { check(this) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val JOB_ID = 53
        private const val CHANNEL = "chat"

        fun schedule(c: Context) {
            c.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(JOB_ID, ComponentName(c, ChatJob::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build())
        }

        fun cancel(c: Context) = c.getSystemService(JobScheduler::class.java).cancel(JOB_ID)

        /** Remember the newest activity the user has seen in a group, so it does not notify again. */
        fun markSeen(c: Context, g: Group) = c.getSharedPreferences("chat_seen", Context.MODE_PRIVATE).edit().putString(g.id, g.lastAt).apply()

        private fun check(c: Context) {
            val chat = Chat(c)
            val me = chat.me() ?: return
            if (!me.approved) return
            val seen = c.getSharedPreferences("chat_seen", Context.MODE_PRIVATE)
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Troop chat", NotificationManager.IMPORTANCE_DEFAULT))
            chat.groups(me).filter { me.uid in it.members && seen.getString(it.id, null) != it.lastAt }.forEach { g ->
                val latest = chat.messages(g.id, after = null, latest = 1).lastOrNull()
                markSeen(c, g)
                if (latest == null || latest.uid == me.uid) return@forEach
                val open = Intent(c, MainActivity::class.java).putExtra("group", g.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                nm.notify(g.id.hashCode(), Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(g.name)
                    .setContentText("${latest.name}: ${latest.text}")
                    .setStyle(Notification.BigTextStyle().bigText("${latest.name}: ${latest.text}"))
                    .setContentIntent(PendingIntent.getActivity(c, g.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    .setAutoCancel(true)
                    .build())
            }
        }
    }
}
