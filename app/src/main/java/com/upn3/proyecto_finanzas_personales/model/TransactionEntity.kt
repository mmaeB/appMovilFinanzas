package com.upn3.proyecto_finanzas_personales.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userEmail: String,
    val amount: Double,
    val description: String,
    val origin: String,
    val type: String, // "INCOME" or "EXPENSE"
    val timestamp: Long
)
