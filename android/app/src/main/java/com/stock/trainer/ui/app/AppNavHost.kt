package com.stock.trainer.ui.app

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stock.trainer.ui.stats.SessionDetailScreen
import com.stock.trainer.ui.stats.StatsScreen
import com.stock.trainer.ui.training.TrainingScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "training",
                    onClick = { navController.navigate("training") { launchSingleTop = true } },
                    icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null) },
                    label = { Text("训练") },
                )
                NavigationBarItem(
                    selected = currentRoute == "stats" || currentRoute?.startsWith("session/") == true,
                    onClick = { navController.navigate("stats") { launchSingleTop = true } },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("统计") },
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "training",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("training") {
                TrainingScreen()
            }
            composable("stats") {
                StatsScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate("session/$sessionId")
                    },
                )
            }
            composable("session/{sessionId}") {
                SessionDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
