package com.upn3.proyecto_finanzas_personales.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String = "",
    val password: String = "",
    val name: String = "",
    val lastname: String = ""
)
