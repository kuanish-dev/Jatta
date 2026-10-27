package com.jatta.study.remind

data class Reminder(val atMs: Long, val text: String)

interface ReminderScheduler {
    fun askPermission()

    fun schedule(reminder: Reminder?)
}
