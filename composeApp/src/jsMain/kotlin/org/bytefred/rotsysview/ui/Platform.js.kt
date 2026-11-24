package org.bytefred.rotsysview.ui

import kotlinx.browser.window

actual val isWeb: Boolean = true

// navigator.platform is deprecated; the user agent names the Mac ("Macintosh")
actual val ctrlClickIsRightClick: Boolean = "Mac" in window.navigator.userAgent

actual val platformMenuGestures = MenuGestures(rightClick = true, longPress = true)
