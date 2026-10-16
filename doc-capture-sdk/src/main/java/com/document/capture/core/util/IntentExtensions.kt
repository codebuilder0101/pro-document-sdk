package com.document.capture.core.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import java.io.Serializable

/**
 * Type-safe, API-level-aware Serializable Intent extra extractor.
 * Uses the non-deprecated typed overload on Android 13 (API 33+) and suppresses
 * the deprecation warning on older APIs with a safe unchecked cast.
 */
inline fun <reified T : Serializable> Intent.getSerializableCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializableExtra(key) as? T
    }

/**
 * Type-safe, API-level-aware Parcelable Intent extra extractor.
 * Uses the non-deprecated typed overload on Android 13 (API 33+).
 */
inline fun <reified T : Parcelable> Intent.getParcelableCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
