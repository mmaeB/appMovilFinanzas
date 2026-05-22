package com.upn3.proyecto_finanzas_personales.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upn3.proyecto_finanzas_personales.model.Transaction
import com.upn3.proyecto_finanzas_personales.model.TransactionType
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var reportType by remember { mutableStateOf(ReportType.DAILY) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.let {
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utcCal.set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            utcCal.set(Calendar.MILLISECOND, 0)
            utcCal.timeInMillis
        }
    )

    val dateRangePickerState = rememberDateRangePickerState()

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startUtc = dateRangePickerState.selectedStartDateMillis
                    val endUtc = dateRangePickerState.selectedEndDateMillis ?: startUtc
                    
                    if (startUtc != null) {
                        // Convertir UTC a Local para el inicio (00:00:00)
                        val startCalUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = startUtc }
                        val startCalLocal = Calendar.getInstance().apply {
                            set(startCalUtc.get(Calendar.YEAR), startCalUtc.get(Calendar.MONTH), startCalUtc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        
                        // Convertir UTC a Local para el fin (23:59:59)
                        val endCalUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = endUtc!! }
                        val endCalLocal = Calendar.getInstance().apply {
                            set(endCalUtc.get(Calendar.YEAR), endCalUtc.get(Calendar.MONTH), endCalUtc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        
                        customStartDate = startCalLocal.timeInMillis
                        customEndDate = endCalLocal.timeInMillis
                    }
                    showDateRangePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancelar") }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Seleccionar rango", modifier = Modifier.padding(16.dp)) },
                headline = { Text("Periodo de reporte", modifier = Modifier.padding(16.dp)) },
                showModeToggle = false,
                modifier = Modifier.fillMaxWidth().height(500.dp)
            )
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportes y Calendario") },
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
        ) {
            // Date Selector Card
            Box(modifier = Modifier.padding(16.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTypeMenu = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = when(reportType) {
                                    ReportType.DAILY -> "REPORTE DIARIO"
                                    ReportType.WEEKLY -> "REPORTE SEMANAL"
                                    ReportType.MONTHLY -> "REPORTE MENSUAL"
                                    ReportType.YEARLY -> "REPORTE ANUAL"
                                    ReportType.CUSTOM -> "RANGO PERSONALIZADO"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = getReportDateRangeText(reportType, selectedDate, customStartDate, customEndDate),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Toca para cambiar periodo o fecha",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.FilterList, contentDescription = null)
                    }
                }

                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Por Día") },
                        leadingIcon = { Icon(Icons.Default.Today, null) },
                        onClick = {
                            reportType = ReportType.DAILY
                            showTypeMenu = false
                            showDatePicker = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Por Semana") },
                        leadingIcon = { Icon(Icons.Default.DateRange, null) },
                        onClick = {
                            reportType = ReportType.WEEKLY
                            showTypeMenu = false
                            showDatePicker = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Por Mes") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                        onClick = {
                            reportType = ReportType.MONTHLY
                            showTypeMenu = false
                            showDatePicker = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Por Año") },
                        leadingIcon = { Icon(Icons.Default.Event, null) },
                        onClick = {
                            reportType = ReportType.YEARLY
                            showTypeMenu = false
                            showDatePicker = true
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Rango Personalizado") },
                        leadingIcon = { Icon(Icons.Default.DateRange, null) },
                        onClick = {
                            reportType = ReportType.CUSTOM
                            showTypeMenu = false
                            showDateRangePicker = true
                        }
                    )
                }
            }

            // Summary Totals
            val filteredTransactions = remember(uiState.transactions, reportType, selectedDate, customStartDate, customEndDate) {
                filterTransactions(uiState.transactions, reportType, selectedDate, customStartDate, customEndDate)
            }

            val totalIncome = filteredTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val totalExpense = filteredTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val totalTransfer = filteredTransactions.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ReportSummaryCard(
                        label = "Ingresos",
                        amount = totalIncome,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    ReportSummaryCard(
                        label = "Gastos",
                        amount = totalExpense,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (totalTransfer > 0) {
                    ReportSummaryCard(
                        label = "Transferencias",
                        amount = totalTransfer,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Transactions List
            Text(
                text = "Detalle de Movimientos",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                val sortedTransactions = filteredTransactions.sortedByDescending { it.timestamp }
                items(sortedTransactions) { transaction ->
                    TransactionReportItem(transaction, uiState.selectedWallet?.currencyCode ?: "S/.")
                }
                
                if (sortedTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hay transacciones en este periodo", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) }
    )
}

@Composable
fun ReportSummaryCard(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(
                String.format("%.2f", amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun TransactionReportItem(transaction: Transaction, currency: String) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
    
    ListItem(
        headlineContent = { Text(transaction.description) },
        supportingContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${dateFormat.format(Date(transaction.timestamp))} - ${timeFormat.format(Date(transaction.timestamp))}")
                Spacer(Modifier.width(8.dp))
                Text("• ${transaction.origin}", color = MaterialTheme.colorScheme.primary)
            }
        },
        trailingContent = {
            val prefix = when(transaction.type) {
                TransactionType.INCOME -> "+"
                TransactionType.EXPENSE -> "-"
                TransactionType.TRANSFER -> "⇄"
            }
            val color = when(transaction.type) {
                TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.secondary
            }
            Text(
                text = "$prefix $currency ${String.format("%.2f", transaction.amount)}",
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

enum class ReportType { DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

fun getReportDateRangeText(type: ReportType, cal: Calendar, customStart: Long? = null, customEnd: Long? = null): String {
    val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val monthF = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val yearF = SimpleDateFormat("yyyy", Locale.getDefault())
    
    return when (type) {
        ReportType.DAILY -> df.format(cal.time)
        ReportType.WEEKLY -> {
            val start = cal.clone() as Calendar
            start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
            val end = start.clone() as Calendar
            end.add(Calendar.DAY_OF_WEEK, 6)
            "${df.format(start.time)} - ${df.format(end.time)}"
        }
        ReportType.MONTHLY -> monthF.format(cal.time).replaceFirstChar { it.uppercase() }
        ReportType.YEARLY -> "Año ${yearF.format(cal.time)}"
        ReportType.CUSTOM -> {
            if (customStart != null && customEnd != null) {
                "${df.format(Date(customStart))} - ${df.format(Date(customEnd))}"
            } else {
                "Seleccionar rango"
            }
        }
    }
}

fun filterTransactions(
    transactions: List<Transaction>, 
    type: ReportType, 
    selectedCal: Calendar,
    customStart: Long? = null,
    customEnd: Long? = null
): List<Transaction> {
    return transactions.filter {
        val transCal = Calendar.getInstance()
        transCal.timeInMillis = it.timestamp
        
        when (type) {
            ReportType.DAILY -> {
                transCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                transCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
            }
            ReportType.WEEKLY -> {
                val start = selectedCal.clone() as Calendar
                start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                
                val end = start.clone() as Calendar
                end.add(Calendar.DAY_OF_YEAR, 7)
                
                it.timestamp >= start.timeInMillis && it.timestamp < end.timeInMillis
            }
            ReportType.MONTHLY -> {
                transCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                transCal.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH)
            }
            ReportType.YEARLY -> {
                transCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR)
            }
            ReportType.CUSTOM -> {
                if (customStart != null && customEnd != null) {
                    it.timestamp >= customStart && it.timestamp <= customEnd
                } else true
            }
        }
    }
}
