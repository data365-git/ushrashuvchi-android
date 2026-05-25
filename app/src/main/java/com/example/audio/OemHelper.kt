package com.example.audio

import android.os.Build

object OemHelper {
    val isXiaomi: Boolean
        get() {
            val mfr = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()
            return mfr == "xiaomi" || mfr == "redmi" || brand == "xiaomi"
                || brand == "redmi" || brand == "poco"
        }
}
