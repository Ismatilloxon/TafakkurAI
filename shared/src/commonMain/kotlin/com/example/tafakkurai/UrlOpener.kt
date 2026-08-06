package com.example.tafakkurai

import androidx.compose.runtime.Composable

/**
 * Opens an external URL in whatever app owns it — for the Telegram sign-in that
 * is a `https://t.me/<bot>?start=<code>` deep link, which Telegram claims and
 * turns into a Start press in the bot chat.
 */
@Composable
expect fun rememberUrlOpener(): (String) -> Unit
