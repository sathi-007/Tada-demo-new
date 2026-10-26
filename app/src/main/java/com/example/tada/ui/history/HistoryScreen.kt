package com.example.tada.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.tada.domain.model.HistoryRow
import com.example.tada.ui.failureMessage
import com.example.tada.ui.theme.TadaMuted
import com.example.tada.ui.theme.TadaYellow

@Composable
fun HistoryScreen(
    onRestored: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is HistoryEvent.ReturnToMap) onRestored()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
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
            state.error != null -> {
                Text(
                    text = failureMessage(state.error!!),
                    color = Color(0xFFB3261E),
                    modifier = Modifier.padding(16.dp),
                )
                TextButton(onClick = viewModel::refresh, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.action_try_again))
                }
            }
            else -> {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
                    TotalColumn(
                        label = stringResource(R.string.total_count),
                        value = state.totalCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    TotalColumn(
                        label = stringResource(R.string.total_price),
                        value = state.totalPrice.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(thickness = 4.dp, color = Color(0xFF9E9E9E))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.id }) { row ->
                        HistoryItem(row, onClick = { viewModel.onRowSelected(row) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TadaMuted)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.history_side_line, stringResource(R.string.side_a), row.aName),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.history_side_line, stringResource(R.string.side_b), row.bName),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}
