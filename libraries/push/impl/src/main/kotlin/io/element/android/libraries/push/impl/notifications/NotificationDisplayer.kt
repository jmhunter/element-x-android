/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2021-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

interface NotificationDisplayer {
    fun showNotification(tag: String?, id: Int, notification: Notification): Boolean
    fun cancelNotification(tag: String?, id: Int)

    /**
     * If the active [statusBarNotification] is displayed as a bubble, hide it from the notification shade but keep the bubble.
     * Cancelling the notification would also remove the bubble, which is not expected when the user reads the room in it.
     * @return true if the notification was displayed as a bubble and has been hidden, false if the caller should cancel it.
     */
    fun hideNotificationIfBubble(statusBarNotification: StatusBarNotification): Boolean
    fun displayDiagnosticNotification(notification: Notification): Boolean
    fun dismissDiagnosticNotification()
    fun displayUnregistrationNotification(notification: Notification): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultNotificationDisplayer(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat
) : NotificationDisplayer {
    override fun showNotification(tag: String?, id: Int, notification: Notification): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Timber.w("Not allowed to notify.")
            return false
        }
        notificationManager.notify(tag, id, notification)
        Timber.d("Notifying with tag: $tag, id: $id")
        return true
    }

    override fun cancelNotification(tag: String?, id: Int) {
        notificationManager.cancel(tag, id)
    }

    override fun hideNotificationIfBubble(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || notification.flags and Notification.FLAG_BUBBLE == 0) {
            return false
        }
        val bubbleMetadata = NotificationCompat.BubbleMetadata.fromPlatform(notification.bubbleMetadata) ?: return false
        if (bubbleMetadata.isNotificationSuppressed) {
            // Already hidden, do not post it again
            return true
        }
        val bubbleIntent = bubbleMetadata.intent ?: return false
        val bubbleIcon = bubbleMetadata.icon ?: return false
        val suppressedBubbleMetadata = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, bubbleIcon)
            .setDesiredHeight(bubbleMetadata.desiredHeight)
            .setAutoExpandBubble(false)
            .setSuppressNotification(true)
            .build()
        val updatedNotification = NotificationCompat.Builder(context, notification)
            .setBubbleMetadata(suppressedBubbleMetadata)
            .setOnlyAlertOnce(true)
            .build()
        return showNotification(statusBarNotification.tag, statusBarNotification.id, updatedNotification)
    }

    override fun displayDiagnosticNotification(notification: Notification): Boolean {
        return showNotification(
            tag = TAG_DIAGNOSTIC,
            id = NOTIFICATION_ID_DIAGNOSTIC,
            notification = notification
        )
    }

    override fun dismissDiagnosticNotification() {
        cancelNotification(
            tag = TAG_DIAGNOSTIC,
            id = NOTIFICATION_ID_DIAGNOSTIC
        )
    }

    override fun displayUnregistrationNotification(notification: Notification): Boolean {
        return showNotification(
            tag = TAG_DIAGNOSTIC,
            id = NOTIFICATION_ID_UNREGISTRATION,
            notification = notification,
        )
    }

    companion object {
        private const val TAG_DIAGNOSTIC = "DIAGNOSTIC"

        /* ==========================================================================================
         * IDs for notifications
         * ========================================================================================== */
        private const val NOTIFICATION_ID_DIAGNOSTIC = 888
        private const val NOTIFICATION_ID_UNREGISTRATION = 889
    }
}
