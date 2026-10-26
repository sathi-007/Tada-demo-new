package com.example.tada.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.tada.R
import com.example.tada.domain.model.FailureReason

@Composable
fun failureMessage(reason: FailureReason): String = stringResource(reason.stringRes())

@StringRes
private fun FailureReason.stringRes(): Int = when (this) {
    FailureReason.Geocode -> R.string.error_geocode
    FailureReason.AirQuality -> R.string.error_air_quality
    FailureReason.Network -> R.string.error_network
    FailureReason.Location -> R.string.error_location
    FailureReason.Permission -> R.string.error_permission
    FailureReason.NicknameTooLong -> R.string.error_nickname
    FailureReason.NotReady -> R.string.error_not_ready
    FailureReason.Booking -> R.string.error_booking
    FailureReason.History -> R.string.error_history
}
