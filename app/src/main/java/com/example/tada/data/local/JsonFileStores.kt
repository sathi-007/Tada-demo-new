package com.example.tada.data.local

import com.example.tada.data.remote.PointDto
import com.example.tada.data.remote.PointsFileDto
import com.example.tada.data.remote.SavedPlaceDto
import com.example.tada.data.remote.StoredBookingDto
import com.example.tada.domain.model.Side
import kotlinx.serialization.json.Json
import java.io.File

class PointsFileStore(
    private val file: File,
    private val json: Json,
) {
    private val lock = Any()

    fun read(): PointsFileDto = synchronized(lock) { readUnlocked() }

    fun upsert(side: Side, point: PointDto) {
        synchronized(lock) {
            val current = readUnlocked()
            val updated = when (side) {
                Side.A -> current.copy(a = point)
                Side.B -> current.copy(b = point)
            }
            writeUnlocked(updated)
        }
    }

    fun clear() {
        synchronized(lock) {
            if (file.exists() && !file.delete()) {
                error("Could not delete ${file.name}")
            }
        }
    }

    private fun readUnlocked(): PointsFileDto {
        if (!file.exists() || file.length() == 0L) return PointsFileDto()
        return json.decodeFromString(PointsFileDto.serializer(), file.readText())
    }

    private fun writeUnlocked(value: PointsFileDto) {
        writeAtomically(file, json.encodeToString(PointsFileDto.serializer(), value))
    }
}

class PlacesFileStore(
    private val file: File,
    private val json: Json,
) {
    private val lock = Any()

    fun read(): List<SavedPlaceDto> = synchronized(lock) { readUnlocked() }

    fun append(place: SavedPlaceDto) {
        synchronized(lock) {
            writeUnlocked(readUnlocked() + place)
        }
    }

    fun write(places: List<SavedPlaceDto>) {
        synchronized(lock) {
            writeUnlocked(places)
        }
    }

    private fun readUnlocked(): List<SavedPlaceDto> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return json.decodeFromString(file.readText())
    }

    private fun writeUnlocked(places: List<SavedPlaceDto>) {
        writeAtomically(file, json.encodeToString(places))
    }
}

class BookingsFileStore(
    private val file: File,
    private val json: Json,
) {
    private val lock = Any()

    fun append(booking: StoredBookingDto) {
        synchronized(lock) {
            val current = readUnlocked()
            writeUnlocked(current + booking)
        }
    }

    fun readNewestFirst(): List<StoredBookingDto> = synchronized(lock) {
        readUnlocked().sortedByDescending { it.timestamp }
    }

    private fun readUnlocked(): List<StoredBookingDto> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return json.decodeFromString(file.readText())
    }

    private fun writeUnlocked(value: List<StoredBookingDto>) {
        writeAtomically(file, json.encodeToString(value))
    }
}

private fun writeAtomically(file: File, contents: String) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, "${file.name}.tmp")
    temporary.writeText(contents)
    if (file.exists() && !file.delete()) {
        temporary.delete()
        error("Could not replace ${file.name}")
    }
    if (!temporary.renameTo(file)) {
        file.writeText(temporary.readText())
        temporary.delete()
    }
}
