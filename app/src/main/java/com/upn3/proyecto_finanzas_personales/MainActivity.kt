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
import com.upn3.proyecto_finanzas_personales.ui.dashboard.DashboardScreen
import com.upn3.proyecto_finanzas_personales.ui.theme.Proyecto_Finanzas_PersonalesTheme
import com.upn3.proyecto_finanzas_personales.ui.transactions.TransactionScreen
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Proyecto_Finanzas_PersonalesTheme {
                FinanceApp()
            }
        }
    }
}

@Composable
fun FinanceApp() {
    val navController = rememberNavController()
    val financeViewModel: FinanceViewModel = viewModel()

    NavHost(navController = navController, startDestination = "auth") {
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
                onLogout = {
                    navController.navigate("auth") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
        composable("transactions") {
            TransactionScreen(
                viewModel = financeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
