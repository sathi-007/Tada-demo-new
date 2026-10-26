package com.example.tada.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tada.domain.model.Side
import com.example.tada.ui.booking.BookingScreen
import com.example.tada.ui.history.HistoryScreen
import com.example.tada.ui.map.MapScreen
import com.example.tada.ui.nickname.NicknameScreen

object Routes {
    const val MAP = "map"
    const val NICKNAME = "nickname/{side}"
    const val BOOKING = "booking"
    const val HISTORY = "history"

    fun nickname(side: Side): String = "nickname/${side.name}"
}

@Composable
fun TadaNavHost() {
    val navController = rememberNavController()
    TadaSafeArea {
    NavHost(
        navController = navController,
        startDestination = Routes.MAP,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.MAP) {
            MapScreen(
                onOpenNickname = { side -> navController.navigate(Routes.nickname(side)) },
                onOpenBooking = { navController.navigate(Routes.BOOKING) },
            )
        }
        composable(
            route = Routes.NICKNAME,
            arguments = listOf(navArgument("side") { type = NavType.StringType }),
        ) {
            NicknameScreen(onSaved = { navController.popBackStack() })
        }
        composable(Routes.BOOKING) {
            BookingScreen(
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onLeaveToMap = {
                    navController.popBackStack(Routes.MAP, inclusive = false)
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onRestored = {
                    navController.popBackStack(Routes.MAP, inclusive = false)
                },
            )
        }
    }
    }
}
