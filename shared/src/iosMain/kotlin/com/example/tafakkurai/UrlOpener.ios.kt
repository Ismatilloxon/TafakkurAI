package com.example.tafakkurai

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberUrlOpener(): (String) -> Unit = { url ->
    NSURL.URLWithString(url)?.let {
        // openURL(_:) alone is deprecated and fails silently on modern iOS — the
        // options/completion variant is the one that actually hands off to Telegram.
        UIApplication.sharedApplication.openURL(it, options = mapOf<Any?, Any?>(), completionHandler = null)
    }
}
