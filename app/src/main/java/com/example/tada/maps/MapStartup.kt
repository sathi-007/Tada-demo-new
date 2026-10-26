package com.example.tada.maps

import com.google.android.gms.common.ConnectionResult

/**
 * The map is composed only after MapsInitializer has returned success and the
 * slot has a real size. Any other result keeps the map out of the tree and
 * asks for another attempt.
 */
internal fun composeMap(initializerStatus: Int, widthPx: Int, heightPx: Int): Boolean =
    initializerStatus == ConnectionResult.SUCCESS && widthPx > 0 && heightPx > 0

internal fun retryMapInit(playServicesStatus: Int, initializerStatus: Int): Boolean =
    playServicesStatus != ConnectionResult.SUCCESS || initializerStatus != ConnectionResult.SUCCESS
