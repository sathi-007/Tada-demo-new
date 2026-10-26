package com.example.tada.domain.usecase

import com.example.tada.domain.model.SavedPlace
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Two places match when latitude and longitude agree through the third decimal.
 * Digits after that are cut off. They are not rounded.
 * 37.5642, 127.0016 matches 37.5645, 127.0018. 37.5650 does not.
 */
class MatchSavedPlace @Inject constructor() {
    fun samePlace(
        leftLatitude: Double,
        leftLongitude: Double,
        rightLatitude: Double,
        rightLongitude: Double,
    ): Boolean = cut(leftLatitude).compareTo(cut(rightLatitude)) == 0 &&
        cut(leftLongitude).compareTo(cut(rightLongitude)) == 0

    fun newest(places: List<SavedPlace>, latitude: Double, longitude: Double): SavedPlace? {
        val index = newestIndex(places, latitude, longitude) ?: return null
        return places[index]
    }

    fun withNickname(
        places: List<SavedPlace>,
        latitude: Double,
        longitude: Double,
        address: String,
        nickname: String,
        savedAtMillis: Long,
    ): List<SavedPlace> {
        val index = newestIndex(places, latitude, longitude)
        if (index == null) {
            return places + SavedPlace(
                latitude = latitude,
                longitude = longitude,
                address = address,
                nickname = nickname,
                savedAtMillis = savedAtMillis,
            )
        }
        return places.mapIndexed { placeIndex, place ->
            if (placeIndex == index) place.copy(nickname = nickname) else place
        }
    }

    private fun newestIndex(places: List<SavedPlace>, latitude: Double, longitude: Double): Int? {
        var best: Int? = null
        places.forEachIndexed { index, place ->
            if (!samePlace(place.latitude, place.longitude, latitude, longitude)) return@forEachIndexed
            val current = best
            if (current == null || place.savedAtMillis >= places[current].savedAtMillis) {
                best = index
            }
        }
        return best
    }

    private fun cut(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(THIRD_DECIMAL, RoundingMode.DOWN)

    private companion object {
        const val THIRD_DECIMAL = 3
    }
}
