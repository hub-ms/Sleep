package com.example.sleep.presentation.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.sleep.presentation.screen.HomeScreen
import com.example.sleep.presentation.tracking.MeasureScreen
import com.example.sleep.presentation.screen.ReportScreen
import com.example.sleep.presentation.screen.SleepMusicSelecterScreen
import com.example.sleep.presentation.viewmodel.SleepMusicViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: SleepMusicViewModel,
    sleepStage: Int
) {
    NavHost(navController, startDestination = "main") {
        composable("main") { HomeScreen(navController) }
        composable(
            route = "measure?mode={mode}&hour={hour}&minute={minute}",
            arguments = listOf(
                navArgument("mode") { defaultValue = "오전" },
                navArgument("hour") { defaultValue = 0 },
                navArgument("minute") { defaultValue = 0 }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "오전"
            val selectedHour = backStackEntry.arguments?.getInt("hour") ?: 0
            val selectedMinute = backStackEntry.arguments?.getInt("minute") ?: 0
            Log.d("selectedTime", "Selected Time-navgraph: $mode $selectedHour:$selectedMinute")
            MeasureScreen(
                navController = navController,
                viewModel = viewModel,
                mode = mode,
                selectedHour = selectedHour,
                selectedMinute = selectedMinute
            )
        }
        composable(
            route = "report"
        ) {
            ReportScreen()
        }
        composable("music") {
            SleepMusicSelecterScreen(
                navController = navController,
                viewModel = viewModel,
            )
        }
    }
}
