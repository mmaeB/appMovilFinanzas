package com.upn3.proyecto_finanzas_personales.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upn3.proyecto_finanzas_personales.model.TransactionType
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel

/**
 * DashboardScreen: La pantalla principal donde el usuario ve su saldo y transacciones.
 * 
 * @param viewModel El ViewModel que provee los datos y maneja las acciones.
 * @param onNavigateToTransactions Navegación hacia la pantalla de añadir transacciones.
 * @param onLogout Acción al cerrar sesión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToTransactions: () -> Unit,
    onLogout: () -> Unit
) {
    // Obtenemos el estado de la UI del ViewModel
    val uiState by viewModel.uiState.collectAsState()
    
    // Estados locales para el diálogo de ajuste de saldo
    var showEditBalanceDialog by remember { mutableStateOf(false) }
    var newBalanceText by remember { mutableStateOf("") }
    
    // El usuario actual para mostrar su nombre
    val currentUser = uiState.currentUser

    // Scaffold provee la estructura básica de Material Design (TopBar, FAB, etc.)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        // Muestra el nombre del usuario configurado en UserRemote
                        Text(
                            text = "Hola, ${currentUser?.name ?: "Usuario"}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Mi Billetera",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    // Botón para salir de la cuenta
                    IconButton(onClick = { viewModel.logout(onLogout) }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar Sesión")
                    }
                }
            )
        },
        // Botón flotante para añadir transacciones (ícono +)
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToTransactions) {
                Icon(Icons.Default.Add, contentDescription = "Añadir Transacción")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Tarjeta que muestra el saldo actual
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Saldo Actual", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // El saldo se formatea a 2 decimales
                        Text(
                            text = "$${String.format("%.2f", uiState.balance)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            // El color principal se puede cambiar en Theme.kt
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Botón para abrir el diálogo de edición de saldo
                        IconButton(onClick = {
                            newBalanceText = uiState.balance.toString()
                            showEditBalanceDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar Saldo")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Transacciones Recientes", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            // Lista eficiente de transacciones
            LazyColumn {
                items(uiState.transactions) { transaction ->
                    ListItem(
                        headlineContent = { Text(transaction.description) },
                        supportingContent = { Text(transaction.origin) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Lógica de colores para Ingresos (+) y Gastos (-)
                                // TIP: Cambia estos códigos hexadecimales para personalizar los colores
                                Text(
                                    text = "${if (transaction.type == TransactionType.INCOME) "+" else "-"}$${String.format("%.2f", transaction.amount)}",
                                    color = if (transaction.type == TransactionType.INCOME) 
                                        Color(0xFF4CAF50) // VERDE para ingresos
                                    else 
                                        Color(0xFFF44336), // ROJO para gastos
                                    fontWeight = FontWeight.Bold
                                )
                                // Botón para eliminar una transacción individual
                                IconButton(onClick = { viewModel.deleteTransaction(transaction.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Borrar",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider() // Línea separadora entre transacciones
                }
            }
        }
    }

    // Ventana emergente (Dialog) para ajustar el saldo manualmente
    if (showEditBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showEditBalanceDialog = false },
            title = { Text("Ajustar Saldo") },
            text = {
                OutlinedTextField(
                    value = newBalanceText,
                    onValueChange = { newBalanceText = it },
                    label = { Text("Nuevo Saldo") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Convierte el texto a número y actualiza
                    val balance = newBalanceText.toDoubleOrNull() ?: 0.0
                    viewModel.updateBalance(balance)
                    showEditBalanceDialog = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBalanceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
