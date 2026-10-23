package com.jatta.study.remind

/** A local notification to show at [atMs] (М19, М20). */
data class Reminder(val atMs: Long, val text: String)

/** Platform side of reminders: Android WorkManager, iOS UNUserNotificationCenter. No server involved. */
interface ReminderScheduler {
    /** Asks for permission to notify, after the first finished session, when a reminder makes sense to the learner. */
    fun askPermission()

    /** Replaces the pending reminder; null cancels it. */
    fun schedule(reminder: Reminder?)
}
