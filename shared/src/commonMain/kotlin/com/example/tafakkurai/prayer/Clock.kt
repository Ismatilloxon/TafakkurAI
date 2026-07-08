package com.example.tafakkurai.prayer

/**
 * Local wall-clock seconds elapsed since midnight (0..86399).
 * Platform-provided so the same timeline logic runs on iOS and Android.
 * Injected into the ViewModel as a lambda, so tests pass a fixed value.
 */
expect fun currentSecondsOfDay(): Int
