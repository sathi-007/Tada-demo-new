package com.example.tada.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class MapsApiKeyTest {
    @Test
    fun blankOrPlaceholderDoesNotCountAsAKey() {
        assertEquals(MapsApiKeyStatus.Missing, mapsApiKeyStatus(null))
        assertEquals(MapsApiKeyStatus.Missing, mapsApiKeyStatus(""))
        assertEquals(MapsApiKeyStatus.Missing, mapsApiKeyStatus("   "))
        assertEquals(MapsApiKeyStatus.Unsubstituted, mapsApiKeyStatus("\${MAPS_API_KEY}"))
        assertEquals(MapsApiKeyStatus.Unsubstituted, mapsApiKeyStatus("MAPS_API_KEY"))
    }

    @Test
    fun substitutedValueIsReady() {
        assertEquals(MapsApiKeyStatus.Ready, mapsApiKeyStatus("local-build-placeholder"))
    }
}
