package com.example.tada.maps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class MapsApiKeyStatus {
    Missing,
    Unsubstituted,
    Ready,
}

/**
 * Maps reads `com.google.android.geo.API_KEY` from the merged manifest.
 * An empty value or a leftover `${MAPS_API_KEY}` placeholder must not start the map.
 */
fun mapsApiKeyStatus(raw: String?): MapsApiKeyStatus {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return MapsApiKeyStatus.Missing
    if (value == "MAPS_API_KEY" || value.contains("\${")) return MapsApiKeyStatus.Unsubstituted
    return MapsApiKeyStatus.Ready
}

/**
 * Value of `com.google.android.geo.API_KEY` in this app's merged manifest.
 * [Context.getPackageName] is the `applicationId` Maps is started with.
 */
fun readMergedMapsApiKey(context: Context): String? {
    val packageName = context.packageName
    val info = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        return null
    }
    return info.metaData?.getString(MAPS_API_KEY_META_DATA)
}

const val MAPS_API_KEY_META_DATA = "com.google.android.geo.API_KEY"
