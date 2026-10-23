package com.jatta.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.jatta.study.judge.OnDeviceModel
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUserDomainMask
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSince1970

/**
 * Entry point for the iOS app (Xcode project to be added once Xcode is installed). Apple Foundation
 * Models is Swift-only, so the Swift side will pass its OnDeviceModel implementation in here.
 */
fun MainViewController(model: OnDeviceModel? = null) = ComposeUIViewController {
    App(
        dataDir = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String,
        nowMs = { (NSDate().timeIntervalSince1970 * 1_000).toLong() },
        utcOffsetMinutes = { (NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate()) / 60).toInt() },
        model = model,
        reminders = IosReminders(),
        speaker = IosSpeaker(),
    )
}
