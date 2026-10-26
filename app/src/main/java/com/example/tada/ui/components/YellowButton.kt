package com.example.tada.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tada.ui.theme.TadaYellow

@Composable
fun YellowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 20.sp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TadaYellow,
            contentColor = Color.Black,
            disabledContainerColor = TadaYellow.copy(alpha = 0.4f),
            disabledContentColor = Color.Black.copy(alpha = 0.4f),
        ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
