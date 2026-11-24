package org.bytefred.rotsysview.ui

actual val isWeb: Boolean = false

actual val ctrlClickIsRightClick: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

actual val platformMenuGestures = MenuGestures(rightClick = true, longPress = false)
