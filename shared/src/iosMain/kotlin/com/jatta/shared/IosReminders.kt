package com.jatta.shared

import com.jatta.study.remind.Reminder
import com.jatta.study.remind.ReminderScheduler
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

private const val REQUEST_ID = "jatta-reminder"

class IosReminders : ReminderScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun askPermission() {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }

    override fun schedule(reminder: Reminder?) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REQUEST_ID))
        if (reminder == null) return
        val seconds = (reminder.atMs / 1_000.0 - NSDate().timeIntervalSince1970).coerceAtLeast(1.0)
        val content = UNMutableNotificationContent().apply {
            setTitle("Jatta")
            setBody(reminder.text)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(REQUEST_ID, content, trigger), withCompletionHandler = null)
    }
}
