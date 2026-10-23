package com.langengine.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUserDomainMask
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSince1970

/** Entry point for the iOS app (Xcode project to be added once Xcode is installed). */
fun MainViewController() = ComposeUIViewController {
    App(
        dataDir = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String,
        nowMs = { (NSDate().timeIntervalSince1970 * 1_000).toLong() },
        utcOffsetMinutes = { (NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate()) / 60).toInt() },
    )
}
