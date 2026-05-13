package com.upn3.proyecto_finanzas_personales.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.upn3.proyecto_finanzas_personales.model.Category
import com.upn3.proyecto_finanzas_personales.model.Transaction
import com.upn3.proyecto_finanzas_personales.model.TransactionType
import com.upn3.proyecto_finanzas_personales.ui.components.NumericKeyboard
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditBalanceDialog by remember { mutableStateOf(false) }
    var newBalanceText by remember { mutableStateOf("") }
    
    var showEditTransactionDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf<Category?>(null) }

    val currentUser = uiState.currentUser
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // Clear error when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearError()
        }
    }

    val onRefresh: () -> Unit = {
        scope.launch {
            isRefreshing = true
            viewModel.loadTransactions()
            delay(1000) // Simular un pequeño retraso para el feedback visual
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
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
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                    IconButton(onClick = { viewModel.logout(onLogout) }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar Sesión")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToTransactions) {
                Icon(Icons.Default.Add, contentDescription = "Añadir Transacción")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Saldo Actual", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "S/.${String.format("%.2f", uiState.balance)}",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = {
                                    newBalanceText = uiState.balance.toString()
                                    showEditBalanceDialog = true
                                    viewModel.clearError()
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar Saldo")
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    StatisticsSection(uiState.transactions)
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Transacciones Recientes", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(uiState.transactions) { transaction ->
                    ListItem(
                        headlineContent = { Text(transaction.description) },
                        supportingContent = { Text(transaction.origin) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${if (transaction.type == TransactionType.INCOME) "+" else "-"}$${String.format("%.2f", transaction.amount)}",
                                    color = if (transaction.type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = {
                                    editingTransaction = transaction
                                    editAmount = transaction.amount.toString()
                                    editDescription = transaction.description
                                    editCategory = uiState.categories.find { it.name == transaction.origin }
                                    showEditTransactionDialog = true
                                    viewModel.clearError()
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Editar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
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
                    HorizontalDivider()
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Espacio para que el FAB no tape la última transacción
                }
            }
        }
    }

    if (showEditBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showEditBalanceDialog = false },
            title = { Text("Ajustar Saldo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newBalanceText,
                        onValueChange = { 
                            val sanitized = it.replace(",", ".")
                            if (sanitized.isEmpty() || sanitized.toDoubleOrNull() != null) {
                                if (!sanitized.contains(".") || sanitized.substringAfter(".").length <= 2) {
                                    newBalanceText = sanitized
                                    viewModel.clearError()
                                }
                            }
                        },
                        label = { Text("Monto del saldo") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                    if (uiState.errorMessage != null && showEditBalanceDialog) {
                        Text(
                            uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val balance = newBalanceText.toDoubleOrNull()
                    if (balance != null && balance >= 0) {
                        viewModel.updateBalance(balance)
                        showEditBalanceDialog = false
                    } else {
                        viewModel.setError("Ingrese un monto válido mayor o igual a 0")
                    }
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

    if (showEditTransactionDialog && editingTransaction != null) {
        val filteredCategories = uiState.categories.filter { it.type == editingTransaction!!.type }
        
        Dialog(
            onDismissRequest = { showEditTransactionDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("EDITAR TRANSACCIÓN") },
                            navigationIcon = {
                                IconButton(onClick = { showEditTransactionDialog = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar")
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
                                .padding(top = 16.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Visualización del Monto
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("MONTO", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.sp))
                                    Text(
                                        text = if (editAmount.isEmpty()) "S/. 0.00" else "S/. $editAmount",
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (editingTransaction!!.type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Descripción
                            OutlinedTextField(
                                value = editDescription,
                                onValueChange = { editDescription = it },
                                label = { Text("Descripción") },
                                placeholder = { Text("¿En qué se usó el dinero?") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyLarge
                            )

                            // Listado de Categorías
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(filteredCategories) { category ->
                                        val isSelected = editCategory?.name == category.name
                                        Surface(
                                            onClick = { editCategory = category },
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
                                            onClick = { 
                                                showEditTransactionDialog = false
                                                onNavigateToCategories()
                                            },
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

                        // Panel Fijo
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
                                    if (key == "." && editAmount.contains(".")) return@NumericKeyboard
                                    if (editAmount.contains(".") && editAmount.substringAfter(".").length >= 2) return@NumericKeyboard
                                    if (editAmount.length < 10) {
                                        editAmount += key
                                        viewModel.clearError()
                                    }
                                },
                                onDelete = { if (editAmount.isNotEmpty()) editAmount = editAmount.dropLast(1) },
                                onClear = { editAmount = "" }
                            )

                            Box(
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.errorMessage != null) {
                                    Text(
                                        uiState.errorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val amt = editAmount.toDoubleOrNull() ?: 0.0
                                    when {
                                        amt <= 0 -> viewModel.setError("El monto debe ser mayor a 0")
                                        editDescription.isBlank() -> viewModel.setError("La descripción es requerida")
                                        editCategory == null -> viewModel.setError("Debe seleccionar una categoría")
                                        else -> {
                                            val updated = editingTransaction!!.copy(
                                                amount = amt,
                                                description = editDescription,
                                                origin = editCategory!!.name
                                            )
                                            viewModel.updateTransaction(updated) {
                                                showEditTransactionDialog = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ACTUALIZAR TRANSACCIÓN", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class ChartType { PIE, BAR }

data class ChartData(
    val label: String,
    val value: Float,
    val color: Color
)

private val ChartColors = listOf(
    Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFD54F),
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF4DB6AC),
    Color(0xFFFF8A65), Color(0xFF90A4AE), Color(0xFF7986CB),
    Color(0xFFF06292), Color(0xFF4DD0E1), Color(0xFFAED581)
)

@Composable
fun StatisticsSection(transactions: List<Transaction>) {
    var viewMode by remember { mutableStateOf(0) } // 0: Gastos, 1: Ingresos, 2: General
    var chartType by remember { mutableStateOf(ChartType.PIE) }
    var selectedItem by remember { mutableStateOf<ChartData?>(null) }

    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.error

    val chartData = remember(transactions, viewMode, incomeColor, expenseColor) {
        when (viewMode) {
            0 -> { // Gastos
                transactions.filter { it.type == TransactionType.EXPENSE }
                    .groupBy { it.origin.ifBlank { "Sin Categoría" } }
                    .mapValues { it.value.sumOf { t -> t.amount }.toFloat() }
                    .toList()
                    .sortedByDescending { it.second }
                    .mapIndexed { index, pair ->
                        ChartData(pair.first, pair.second, ChartColors[index % ChartColors.size])
                    }
            }
            1 -> { // Ingresos
                transactions.filter { it.type == TransactionType.INCOME }
                    .groupBy { it.origin.ifBlank { "Sin Categoría" } }
                    .mapValues { it.value.sumOf { t -> t.amount }.toFloat() }
                    .toList()
                    .sortedByDescending { it.second }
                    .mapIndexed { index, pair ->
                        ChartData(pair.first, pair.second, ChartColors[index % ChartColors.size])
                    }
            }
            else -> { // General (Ambos)
                val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat()
                val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat()
                listOf(
                    ChartData("Ingresos", totalIncome, incomeColor),
                    ChartData("Gastos", totalExpense, expenseColor)
                ).filter { it.value > 0 }
            }
        }
    }

    // Reset selection when data changes
    LaunchedEffect(viewMode) { selectedItem = null }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when(viewMode) {
                        0 -> "Análisis de Gastos"
                        1 -> "Análisis de Ingresos"
                        else -> "Balance General"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))) {
                    IconButton(onClick = { chartType = ChartType.PIE }) {
                        Icon(
                            Icons.Default.PieChart,
                            contentDescription = "Circular",
                            tint = if (chartType == ChartType.PIE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { chartType = ChartType.BAR }) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Barras",
                            tint = if (chartType == ChartType.BAR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    label = { Text("Gastos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.error
                    )
                )
                FilterChip(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    label = { Text("Ingresos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
                FilterChip(
                    selected = viewMode == 2,
                    onClick = { viewMode = 2 },
                    label = { Text("General") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (chartData.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay transacciones para analizar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1.2f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (chartType == ChartType.PIE) {
                            PieChart(chartData, selectedItem, modifier = Modifier.size(140.dp))
                        } else {
                            BarChart(chartData, selectedItem, { selectedItem = it }, modifier = Modifier.fillMaxSize().padding(8.dp))
                        }
                        
                        if (selectedItem != null && chartType == ChartType.PIE) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "S/.${String.format("%.0f", selectedItem!!.value)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        chartData.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedItem = if (selectedItem == item) null else item }
                                    .background(
                                        if (selectedItem == item) item.color.copy(alpha = 0.1f) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(4.dp)
                            ) {
                                Box(modifier = Modifier.size(10.dp).background(item.color, RoundedCornerShape(2.dp)))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selectedItem == item) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (selectedItem == item) {
                                        Text(
                                            "S/.${String.format("%.2f", item.value)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PieChart(data: List<ChartData>, selectedItem: ChartData?, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    
    Canvas(modifier = modifier) {
        var startAngle = -90f
        data.forEach { item ->
            val sweepAngle = if (total > 0) (item.value / total) * 360f else 0f
            val isSelected = item == selectedItem
            
            drawArc(
                color = item.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = if (isSelected) 35.dp.toPx() else 25.dp.toPx())
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun BarChart(data: List<ChartData>, selectedItem: ChartData?, onItemSelected: (ChartData) -> Unit, modifier: Modifier = Modifier) {
    val maxValue = data.maxOfOrNull { it.value } ?: 1f
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.take(5).forEach { item ->
            val isSelected = item == selectedItem
            // Calculamos la altura relativa (mínimo 10% para que se vea algo si el valor es muy bajo)
            val barHeightFraction = if (maxValue > 0) (item.value / maxValue).coerceIn(0.1f, 1f) else 0.1f
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onItemSelected(item) }
            ) {
                // Contenedor de la barra para dar espacio y centrar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f) // Barra un poco más delgada para mejor estética
                            .fillMaxHeight(barHeightFraction)
                            .background(
                                if (isSelected) item.color else item.color.copy(alpha = 0.4f),
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

