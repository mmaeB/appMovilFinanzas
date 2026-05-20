package com.upn3.proyecto_finanzas_personales.model

import java.util.UUID

data class Wallet(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val currencyCode: String = "PEN",
    val balance: Double = 0.0,
    val color: Long = 0xFF4CAF50
)
