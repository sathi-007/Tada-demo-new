package com.example.tada.domain

import com.example.tada.data.remote.AdminAreaDto
import com.example.tada.data.remote.LocalityInfoDto
import com.example.tada.data.remote.ReverseGeocodeDto
import com.example.tada.data.remote.toAdminAreas
import com.example.tada.domain.model.AdminArea
import com.example.tada.domain.model.MapPoint
import com.example.tada.domain.usecase.FormatAdministrativeAddress
import com.example.tada.domain.usecase.PointRowLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressAndLabelTest {
    private val format = FormatAdministrativeAddress()

    @Test
    fun joinsTheTwoHighestOrdersAscending() {
        val areas = listOf(
            AdminArea("Yangjae 2(i)-dong", 8),
            AdminArea("South Korea", 2),
            AdminArea("Seocho District", 6),
            AdminArea("Seoul", 4),
        )

        assertEquals("Seocho District, Yangjae 2(i)-dong", format.format(areas))
    }

    @Test
    fun ignoresBlankNamesAndReturnsNullWhenNothingRemains() {
        assertEquals("Only", format.format(listOf(AdminArea(" ", 3), AdminArea("Only", 1))))
        assertNull(format.format(emptyList()))
        assertNull(format.format(listOf(AdminArea("  ", 9))))
    }

    @Test
    fun mapperDropsInformativeAreas() {
        val dto = ReverseGeocodeDto(
            localityInfo = LocalityInfoDto(
                administrative = listOf(
                    AdminAreaDto("South Korea", 2),
                    AdminAreaDto("Seocho District", 6),
                    AdminAreaDto("Yangjae 2(i)-dong", 8),
                    AdminAreaDto(" ", 9),
                    AdminAreaDto(null, 10),
                ),
                informative = listOf(AdminAreaDto("cafe that must not appear", 99)),
            ),
        )

        val address = format.format(dto.toAdminAreas())

        assertEquals("Seocho District, Yangjae 2(i)-dong", address)
    }

    @Test
    fun rowShowsNicknameWhenPresentAndAddressWhenBlank() {
        val labels = PointRowLabel()
        val point = MapPoint(1.0, 2.0, "Seocho District, Yangjae 2(i)-dong", 12, "")

        assertEquals("", labels.label(null))
        assertEquals(point.address, labels.label(point))
        assertEquals("home", labels.label(point.copy(nickname = "home")))
        assertEquals(point.address, labels.label(point.copy(nickname = "   ".trim().let { "" })))
    }
}
