package com.example.tada.domain.usecase

import com.example.tada.domain.model.MapPoint
import javax.inject.Inject

class PointRowLabel @Inject constructor() {
    fun label(point: MapPoint?): String {
        if (point == null) return ""
        return point.nickname.ifBlank { point.address }
    }
}
