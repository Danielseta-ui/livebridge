package com.appsfolder.livebridge.liveupdate.display

import android.os.Build

internal object LiveUpdateSdk {
    const val NATIVE_LIVE_UPDATES_SDK = 36

    fun supportsNativeLiveUpdates(): Boolean {
        return Build.VERSION.SDK_INT >= NATIVE_LIVE_UPDATES_SDK
    }

    fun requiresOverlayDisplay(): Boolean {
        return !supportsNativeLiveUpdates()
    }
}
