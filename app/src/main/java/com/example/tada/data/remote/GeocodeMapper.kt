package com.example.tada.data.remote

import com.example.tada.domain.model.AdminArea

fun ReverseGeocodeDto.toAdminAreas(): List<AdminArea> =
    localityInfo?.administrative.orEmpty().mapNotNull { area ->
        val name = area.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val order = area.order ?: return@mapNotNull null
        AdminArea(name = name, order = order)
    }
