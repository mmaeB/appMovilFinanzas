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
import com.upn3.proyecto_finanzas_personales.ui.dashboard.DashboardScreen
import com.upn3.proyecto_finanzas_personales.ui.theme.Proyecto_Finanzas_PersonalesTheme
import com.upn3.proyecto_finanzas_personales.ui.transactions.TransactionScreen
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel

/**
 * ESTA ES LA PUERTA DE ENTRADA DE TU APLICACIÓN
 * 
 * En Android, una "Activity" es como una página en blanco donde dibujamos la app.
 * MainActivity es la primera página que se abre cuando tocas el ícono de la app.
 */
class MainActivity : ComponentActivity() {
    // onCreate: Es el primer método que se ejecuta. Es como el botón de "Encendido".
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hace que la app ocupe toda la pantalla, incluyendo la barra de arriba (batería, hora).
        enableEdgeToEdge()
        
        // setContent: Aquí es donde decimos "Dibuja esto en la pantalla".
        setContent {
            // Aplicamos el tema visual (colores y letras) que definiste para tu proyecto.
            Proyecto_Finanzas_PersonalesTheme {
                // Llamamos a la función principal que controla toda la app.
                FinanceApp()
            }
        }
    }
}

/**
 * EL CONTROLADOR DE PANTALLAS (FinanceApp)
 * 
 * Esta función es como un "director de orquesta". Se encarga de saber qué pantalla
 * debe mostrarse en cada momento (Login, Inicio o Transacciones).
 */
@Composable
fun FinanceApp() {
    // navController: Es como el GPS de la app. Sabe cómo ir de una pantalla a otra.
    val navController = rememberNavController()
    
    // financeViewModel: Es el "Cerebro" compartido. Todas las pantallas lo usan para
    // guardar o pedir datos de finanzas.
    val financeViewModel: FinanceViewModel = viewModel()

    // NavHost: Es el mapa de navegación. Aquí registramos todas nuestras "Rutas".
    NavHost(navController = navController, startDestination = "auth") {
        
        // RUTA 1: Pantalla de Autenticación (Login/Registro)
        composable("auth") {
            AuthScreen(
                viewModel = financeViewModel,
                onLoginSuccess = {
                    // Si el login es exitoso, el GPS nos lleva al "dashboard" (Inicio).
                    navController.navigate("dashboard") {
                        // Borra la pantalla de login del historial para que no puedas volver atrás con el botón físico.
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        
        // RUTA 2: Pantalla Principal (Dashboard)
        composable("dashboard") {
            DashboardScreen(
                viewModel = financeViewModel,
                // Si el usuario quiere añadir una transacción, lo mandamos a esa pantalla.
                onNavigateToTransactions = { navController.navigate("transactions") },
                onLogout = {
                    // Si cierra sesión, lo mandamos de vuelta al principio (Login).
                    navController.navigate("auth") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
        
        // RUTA 3: Pantalla para añadir Transacciones
        composable("transactions") {
            TransactionScreen(
                viewModel = financeViewModel,
                // Cuando termina de guardar, simplemente cerramos esta pantalla para volver a la anterior.
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
