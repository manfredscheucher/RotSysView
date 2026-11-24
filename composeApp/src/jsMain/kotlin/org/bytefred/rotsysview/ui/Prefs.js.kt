package org.bytefred.rotsysview.ui

import kotlinx.browser.localStorage

// browser storage can be unavailable (private mode, blocked site data): then nothing is remembered
actual fun loadPref(key: String): String? = try { localStorage.getItem("rotsysview.$key") } catch (e: Throwable) { null }

actual fun savePref(key: String, value: String) {
    try { localStorage.setItem("rotsysview.$key", value) } catch (e: Throwable) { }
}

actual fun removePref(key: String) {
    try { localStorage.removeItem("rotsysview.$key") } catch (e: Throwable) { }
}
