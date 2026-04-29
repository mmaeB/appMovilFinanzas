package com.upn3.proyecto_finanzas_personales.model

import java.util.UUID

/**
 * TransactionType: Define si el movimiento de dinero es una entrada o una salida.
 */
enum class TransactionType {
    INCOME,  // Representa un Ingreso (ej. Sueldo, Venta)
    EXPENSE  // Representa un Gasto (ej. Comida, Alquiler)
}

/**
 * Transaction: Modelo de datos para representar un movimiento financiero.
 * Se usa para mostrar la lista de transacciones en la pantalla principal.
 */
data class Transaction(
    // Genera un ID único automáticamente para cada transacción
    val id: String = UUID.randomUUID().toString(),
    // El monto o cantidad de dinero
    val amount: Double,
    // Breve descripción del movimiento
    val description: String,
    // Lugar de donde viene o a donde va el dinero (ej: BCP, Yape, Efectivo)
    val origin: String,
    // Tipo de transacción (INCOME o EXPENSE)
    val type: TransactionType,
    // Marca de tiempo (fecha y hora) de creación
    val timestamp: Long = System.currentTimeMillis()
)
