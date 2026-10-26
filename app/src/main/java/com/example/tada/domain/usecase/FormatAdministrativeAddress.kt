package com.example.tada.domain.usecase

import com.example.tada.domain.model.AdminArea
import javax.inject.Inject

/**
 * Takes the two administrative areas with the highest [AdminArea.order] and joins
 * their names in ascending order. Informative areas are not part of this input.
 * Fewer than two named areas returns those names. None returns null.
 */
class FormatAdministrativeAddress @Inject constructor() {
    fun format(areas: List<AdminArea>): String? {
        val chosen = areas
            .asSequence()
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.order }
            .take(2)
            .sortedBy { it.order }
            .map { it.name }
            .toList()
        if (chosen.isEmpty()) return null
        return chosen.joinToString(", ")
    }
}
