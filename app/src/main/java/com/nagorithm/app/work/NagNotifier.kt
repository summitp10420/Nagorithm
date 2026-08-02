package com.nagorithm.app.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nagorithm.app.MainActivity
import com.nagorithm.app.R
import com.nagorithm.app.verdict.Nag

object NagNotifier {

    private const val CHANNEL_ID = "nags"

    /**
     * One id, reused. A backlog of stale nags in the shade is noise — only the current verdict is
     * ever true, so a new one replaces the old rather than queueing behind it.
     */
    private const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Nags")
                .setDescription("Check-ins on whether you are getting anywhere.")
                .build(),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns whether it actually reached the shade, which is what gets recorded against the nag. */
    fun post(context: Context, nag: Nag): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nag)
            .setContentTitle(nag.headline)
            .setContentText(nag.body)
            // The body carries the numbers that make the nag credible, so it has to be readable
            // without opening the app.
            .setStyle(NotificationCompat.BigTextStyle().bigText(nag.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}
