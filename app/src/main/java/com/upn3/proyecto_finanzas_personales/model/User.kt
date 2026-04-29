package com.upn3.proyecto_finanzas_personales.model

/**
 * CLASE DE USUARIO (MODELO)
 * 
 * Esta clase representa a un usuario dentro de nuestra aplicación.
 * Es como una "ficha" donde guardamos su información básica.
 * 
 * Como no estamos usando base de datos local (Room), es una clase simple (data class).
 */
data class User(
    // El correo sirve como identificador único
    val email: String = "",
    // La contraseña para validar el ingreso
    val password: String = "",
    // El nombre del usuario
    val name: String = "",
    // El apellido del usuario
    val lastname: String = ""
)
