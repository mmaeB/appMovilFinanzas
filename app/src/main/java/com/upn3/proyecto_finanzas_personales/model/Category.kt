package com.upn3.proyecto_finanzas_personales.model

import java.util.UUID

data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val icon: String = "Category" // Podría ser un nombre de icono o un string
)
