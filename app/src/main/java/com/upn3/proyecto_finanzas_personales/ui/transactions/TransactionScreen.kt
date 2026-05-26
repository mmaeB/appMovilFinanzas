package com.upn3.proyecto_finanzas_personales.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.upn3.proyecto_finanzas_personales.model.Category
import com.upn3.proyecto_finanzas_personales.model.TransactionType
import com.upn3.proyecto_finanzas_personales.ui.components.NumericKeyboard
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.let {
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utcCal.set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            utcCal.set(Calendar.MILLISECOND, 0)
            utcCal.timeInMillis
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        val localCal = Calendar.getInstance().apply {
                            set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH),
                                0, 0, 0
                            )
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedDate = localCal
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Filtrar categorías por tipo (Ingreso o Gasto)
    val filteredCategories = uiState.categories.filter { it.type == type }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NUEVA TRANSACCIÓN") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToCategories) {
                        Text("GESTIONAR")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector de Tipo (Ingreso / Gasto)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(2.dp)
                ) {
                    Button(
                        onClick = { 
                            type = TransactionType.INCOME 
                            selectedCategory = null
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (type == TransactionType.INCOME) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = null
                    ) {
                        Text("INGRESO", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { 
                            type = TransactionType.EXPENSE 
                            selectedCategory = null
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else Color.Transparent,
                            contentColor = if (type == TransactionType.EXPENSE) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = null
                    ) {
                        Text("GASTO", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Visualización del Monto (Lectura con panel numérico)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val symbol = viewModel.getCurrencySymbol(uiState.selectedWallet?.currencyCode ?: "PEN")
                        Text("MONTO ($symbol)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.sp))
                        Text(
                            text = if (amount.isEmpty()) "$symbol 0.00" else "$symbol $amount",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Descripción (Activa teclado estándar)
                OutlinedTextField(
                    value = description,
                    onValueChange = { 
                        description = it
                        viewModel.clearError()
                    },
                    label = { Text("Descripción") },
                    placeholder = { Text("¿En qué se usó?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                // Fecha de la transacción
                OutlinedTextField(
                    value = dateFormatter.format(selectedDate.time),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Fecha") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    enabled = false, // Hacemos que solo el click sea interactivo
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Listado de Categorías Horizontal
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Categoría", style = MaterialTheme.typography.labelLarge)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredCategories) { category ->
                            val isSelected = selectedCategory == category
                            Surface(
                                onClick = { 
                                    selectedCategory = category
                                    viewModel.clearError()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                    Text(category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        item {
                            Surface(
                                onClick = onNavigateToCategories,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text("NUEVA", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Panel Fijo al fondo: Teclado Numérico y Botón Guardar
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 24.dp, end = 24.dp, bottom = 12.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                NumericKeyboard(
                    onKeyPress = { key ->
                        if (key == "." && amount.contains(".")) return@NumericKeyboard
                        if (amount.contains(".") && amount.substringAfter(".").length >= 2) return@NumericKeyboard
                        if (amount.length < 10) {
                            amount += key
                            viewModel.clearError()
                        }
                    },
                    onDelete = {
                        if (amount.isNotEmpty()) amount = amount.dropLast(1)
                    },
                    onClear = { amount = "" }
                )

                Box(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        when {
                            amt <= 0 -> viewModel.setError("El monto debe ser mayor a 0")
                            description.isBlank() -> viewModel.setError("La descripción es requerida")
                            selectedCategory == null -> viewModel.setError("Debe seleccionar una categoría")
                            else -> {
                                viewModel.addTransactionWithDate(amt, description, selectedCategory!!.name, type, selectedDate.timeInMillis) {
                                    onNavigateBack()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("GUARDAR TRANSACCIÓN", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    
    // Clear error when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearError()
        }
    }
}
