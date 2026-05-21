package com.upn3.proyecto_finanzas_personales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.upn3.proyecto_finanzas_personales.ui.auth.AuthScreen
import com.upn3.proyecto_finanzas_personales.ui.auth.RegisterScreen
import com.upn3.proyecto_finanzas_personales.ui.categories.CategoryScreen
import com.upn3.proyecto_finanzas_personales.ui.dashboard.DashboardScreen
import com.upn3.proyecto_finanzas_personales.ui.profile.ProfileScreen
import com.upn3.proyecto_finanzas_personales.ui.theme.Proyecto_Finanzas_PersonalesTheme
import com.upn3.proyecto_finanzas_personales.ui.transactions.TransactionScreen
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val financeViewModel: FinanceViewModel = viewModel()
            val uiState by financeViewModel.uiState.collectAsState()
            
            Proyecto_Finanzas_PersonalesTheme(theme = uiState.selectedTheme) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    FinanceApp(financeViewModel)
                }
            }
        }
    }
}

@Composable
fun FinanceApp(financeViewModel: FinanceViewModel) {
    val navController = rememberNavController()
    val uiState by financeViewModel.uiState.collectAsState()
    val startDestination = if (uiState.currentUser != null) "dashboard" else "auth"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") {
            AuthScreen(
                viewModel = financeViewModel,
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }
        composable("register") {
            RegisterScreen(
                viewModel = financeViewModel,
                onRegisterSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                viewModel = financeViewModel,
                onNavigateToTransactions = { navController.navigate("transactions") },
                onNavigateToCategories = { navController.navigate("categories") },
                onLogout = {
                    navController.navigate("auth") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToReports = { navController.navigate("reports") }
            )
        }
        composable("reports") {
            com.upn3.proyecto_finanzas_personales.ui.reports.ReportsScreen(
                viewModel = financeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("profile") {
            ProfileScreen(
                viewModel = financeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("transactions") {
            TransactionScreen(
                viewModel = financeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCategories = { navController.navigate("categories") }
            )
        }
        composable("categories") {
            CategoryScreen(
                viewModel = financeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
