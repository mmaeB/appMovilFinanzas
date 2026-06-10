package com.upn3.proyecto_finanzas_personales.model

import java.util.UUID

enum class TransactionType {
    INCOME, EXPENSE, TRANSFER
}

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val walletId: String = "default",
    val amount: Double = 0.0,
    val currencyCode: String = "PEN",
    val description: String = "",
    val origin: String = "",
    val type: TransactionType = TransactionType.INCOME,
    val timestamp: Long = System.currentTimeMillis()
)