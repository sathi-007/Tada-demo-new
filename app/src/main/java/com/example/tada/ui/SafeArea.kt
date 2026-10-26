package com.example.tada.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.tada.ui.theme.TadaSafeBlue

/**
 * Paints the status-bar and navigation/gesture insets in the screenshot blue
 * and keeps screen content inside those system bars.
 */
@Composable
fun TadaSafeArea(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            content = content,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(TadaSafeBlue),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(TadaSafeBlue),
        )
    }
}
