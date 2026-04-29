package com.upn3.proyecto_finanzas_personales.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.upn3.proyecto_finanzas_personales.model.TransactionType
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel
import kotlinx.coroutines.flow.update

/**
 * TransactionScreen: Pantalla para registrar nuevos ingresos o gastos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    // Estados locales para los campos del formulario
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) } // Gasto por defecto

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Transacción") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Fila para seleccionar entre Ingreso o Gasto
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(
                    selected = type == TransactionType.INCOME,
                    onClick = { type = TransactionType.INCOME },
                    label = { Text("Ingreso") }
                )
                FilterChip(
                    selected = type == TransactionType.EXPENSE,
                    onClick = { type = TransactionType.EXPENSE },
                    label = { Text("Gasto") }
                )
            }

            // Campo para ingresar el monto (acepta decimales)
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Monto") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Campo para la descripción de la transacción (ej: Comida, Sueldo)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )

            // Campo para el origen o categoría (ej: Efectivo, Banco, Yape)
            OutlinedTextField(
                value = origin,
                onValueChange = { origin = it },
                label = { Text("Origen/Categoría") },
                modifier = Modifier.fillMaxWidth()
            )

            // Spacer con weight(1f) empuja el botón hacia la parte inferior de la pantalla
            Spacer(modifier = Modifier.weight(1f))


            // Botón para guardar y registrar la transacción en el ViewModel
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    // Solo guardamos si el monto es válido y hay una descripción
                    if (amt > 0 && description.isNotBlank()) {

                        viewModel.addTransaction(amt, description, origin, type)
                        onNavigateBack() // Regresa a la pantalla principal
                    }

                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar")
            }
        }
    }
}
