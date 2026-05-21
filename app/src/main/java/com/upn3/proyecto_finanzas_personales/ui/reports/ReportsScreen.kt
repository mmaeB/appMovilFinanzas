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

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.timeInMillis
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = it
                        selectedDate = cal
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
            // Report Type Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportTypeChip(
                    label = "Diario",
                    selected = reportType == ReportType.DAILY,
                    onClick = { reportType = ReportType.DAILY }
                )
                ReportTypeChip(
                    label = "Semanal",
                    selected = reportType == ReportType.WEEKLY,
                    onClick = { reportType = ReportType.WEEKLY }
                )
                ReportTypeChip(
                    label = "Mensual",
                    selected = reportType == ReportType.MONTHLY,
                    onClick = { reportType = ReportType.MONTHLY }
                )
                ReportTypeChip(
                    label = "Anual",
                    selected = reportType == ReportType.YEARLY,
                    onClick = { reportType = ReportType.YEARLY }
                )
            }

            // Date Selector Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { showDatePicker = true },
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
                            text = getReportDateRangeText(reportType, selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Toca para cambiar fecha",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                }
            }

            // Summary Totals
            val filteredTransactions = remember(uiState.transactions, reportType, selectedDate) {
                filterTransactions(uiState.transactions, reportType, selectedDate)
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

enum class ReportType { DAILY, WEEKLY, MONTHLY, YEARLY }

fun getReportDateRangeText(type: ReportType, cal: Calendar): String {
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
    }
}

fun filterTransactions(transactions: List<Transaction>, type: ReportType, selectedCal: Calendar): List<Transaction> {
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
        }
    }
}
