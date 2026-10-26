package com.example.tada.ui.booking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tada.R
import com.example.tada.domain.model.SidePresentation
import com.example.tada.ui.components.YellowButton
import com.example.tada.ui.failureMessage
import com.example.tada.ui.theme.TadaMuted
import com.example.tada.ui.theme.TadaYellow

@Composable
fun BookingScreen(
    onOpenHistory: () -> Unit,
    onLeaveToMap: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    BackHandler {
        viewModel.leave(onLeaveToMap)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        when {
            state.loading -> {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator(
                    color = TadaYellow,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            state.presentation != null && state.price != null -> {
                SideBlock(letter = stringResource(R.string.side_a), side = state.presentation!!.a)
                Spacer(modifier = Modifier.height(28.dp))
                SideBlock(letter = stringResource(R.string.side_b), side = state.presentation!!.b)
                Spacer(modifier = Modifier.weight(1f))
                LabelValue(
                    label = stringResource(R.string.price),
                    value = state.price.toString(),
                    emphasizeValue = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
                YellowButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = onOpenHistory,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                )
            }
            else -> {
                state.presentation?.let { presentation ->
                    SideBlock(letter = stringResource(R.string.side_a), side = presentation.a)
                    Spacer(modifier = Modifier.height(28.dp))
                    SideBlock(letter = stringResource(R.string.side_b), side = presentation.b)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = failureMessage(state.error ?: com.example.tada.domain.model.FailureReason.Booking),
                    color = Color(0xFFB3261E),
                )
                TextButton(onClick = viewModel::retry) {
                    Text(stringResource(R.string.action_try_again))
                }
            }
        }
    }
}

@Composable
private fun SideBlock(letter: String, side: SidePresentation) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = letter, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = side.locationName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
    Spacer(modifier = Modifier.height(12.dp))
    LabelValue(label = stringResource(R.string.aqi), value = side.aqi.toString())
    Spacer(modifier = Modifier.height(8.dp))
    LabelValue(label = stringResource(R.string.nickname), value = side.nickname)
}

@Composable
private fun LabelValue(label: String, value: String, emphasizeValue: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = TadaMuted, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontWeight = if (emphasizeValue) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (emphasizeValue) 22.sp else 16.sp,
        )
    }
}
