package com.example.tada.ui.nickname

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.example.tada.domain.model.Side
import com.example.tada.ui.components.YellowButton
import com.example.tada.ui.failureMessage
import com.example.tada.ui.theme.TadaMuted

@Composable
fun NicknameScreen(
    onSaved: () -> Unit,
    viewModel: NicknameViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is NicknameEvent.Saved) onSaved()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = if (state.side == Side.A) stringResource(R.string.side_a) else stringResource(R.string.side_b),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = state.address, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.aqi),
                        color = TadaMuted,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = state.aqi?.toString().orEmpty(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        state.error?.let { reason ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = failureMessage(reason), color = Color(0xFFB3261E))
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = state.nickname,
            onValueChange = viewModel::onNicknameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.nickname), fontWeight = FontWeight.Medium) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        YellowButton(
            text = stringResource(R.string.action_confirm),
            onClick = viewModel::save,
            enabled = !state.saving && !state.missing,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )
    }
}
