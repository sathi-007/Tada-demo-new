package com.example.tada.maps

import com.google.android.gms.common.ConnectionResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStartupTest {
    @Test
    fun mapIsComposedOnlyAfterSuccessAndARealSize() {
        assertTrue(composeMap(ConnectionResult.SUCCESS, widthPx = 1080, heightPx = 1600))
        assertFalse(composeMap(ConnectionResult.SUCCESS, widthPx = 0, heightPx = 1600))
        assertFalse(composeMap(ConnectionResult.SUCCESS, widthPx = 1080, heightPx = 0))
        assertFalse(composeMap(ConnectionResult.SERVICE_UPDATING, widthPx = 1080, heightPx = 1600))
        assertFalse(composeMap(ConnectionResult.INTERNAL_ERROR, widthPx = 1080, heightPx = 1600))
    }

    @Test
    fun failedPlayServicesOrInitializerIsRetried() {
        assertFalse(retryMapInit(ConnectionResult.SUCCESS, ConnectionResult.SUCCESS))
        assertTrue(retryMapInit(ConnectionResult.SERVICE_UPDATING, ConnectionResult.SUCCESS))
        assertTrue(retryMapInit(ConnectionResult.SUCCESS, ConnectionResult.INTERNAL_ERROR))
        assertTrue(retryMapInit(ConnectionResult.SERVICE_MISSING, ConnectionResult.SERVICE_MISSING))
    }
}