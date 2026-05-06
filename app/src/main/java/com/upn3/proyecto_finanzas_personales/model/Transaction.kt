package com.upn3.proyecto_finanzas_personales.model

import java.util.UUID

enum class TransactionType {
    INCOME, EXPENSE
}

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val description: String,
    val origin: String,
    val type: TransactionType,
    val timestamp: Long = System.currentTimeMillis()
)
