package com.upn3.proyecto_finanzas_personales.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.upn3.proyecto_finanzas_personales.viewmodel.FinanceViewModel
import androidx.compose.material.icons.filled.AccountCircle

/**
 * ESTA ES LA PANTALLA DE INICIO (LOGIN Y REGISTRO)
 * 
 * ¿Qué es un @Composable? 
 * Imagina que es como una pieza de LEGO. Es una función que dibuja una parte de la pantalla.
 * 
 * @param viewModel: Es el "cerebro" que sabe si el usuario existe o no.
 * @param onLoginSuccess: Es una instrucción que dice: "Si todo sale bien, llévame a la otra pantalla".
 */
@Composable
fun AuthScreen(viewModel: FinanceViewModel, onLoginSuccess: () -> Unit) {
    
    /**
     * LAS VARIABLES DE MEMORIA (ESTADOS)
     * 
     * Usamos "remember" para que la aplicación no se olvide de lo que escribiste.
     * Si no pusiéramos esto, cada vez que la pantalla se mueva un poco, los textos se borrarían.
     */
    
    // isLogin: Es como un interruptor. "true" significa que estamos entrando, "false" que nos estamos registrando.
    var isLogin by remember { mutableStateOf(true) }
    
    // Aquí guardamos lo que el usuario va escribiendo en los cuadritos de texto.
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    
    // uiState: Aquí la pantalla se queda "escuchando" al cerebro (ViewModel) por si hay errores o cambios.
    val uiState by viewModel.uiState.collectAsState()
    
    // scrollState: Permite que podamos bajar y subir con el dedo si los botones no caben en la pantalla.
    val scrollState = rememberScrollState()

    /**
     * DISEÑO DE LA PANTALLA
     * 
     * Column: Organiza todo uno debajo de otro (como una torre).
     */
    Column(
        modifier = Modifier
            .fillMaxSize() // Ocupa todo el tamaño del celular
            .padding(16.dp) // Deja un pequeño espacio en los bordes para que no esté pegado
            .verticalScroll(scrollState), // Activa el poder subir y bajar
        horizontalAlignment = Alignment.CenterHorizontally, // Centra todo a lo ancho
        verticalArrangement = Arrangement.Center // Centra todo a lo alto
    ) {
        
        // Icon: Es una imagen o dibujito. Aquí ponemos el círculo de "Usuario".
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            // colorScheme.primary: Es el color principal que elegiste para tu app.
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp)) // Espacio vacío para separar cosas

        // Text: Escribe un mensaje en la pantalla.
        Text(
            text = if (isLogin) "¡Bienvenido de nuevo!" else "Crea tu cuenta",
            style = MaterialTheme.typography.headlineMedium // Hace la letra grande
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        /**
         * LOS CUADRITOS DE TEXTO (Campos de entrada)
         * 
         * Solo mostramos Nombres y Apellidos si el interruptor 'isLogin' está apagado (estamos registrando).
         */
        if (!isLogin) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it }, // "Cuando el usuario escriba, guarda el texto en la variable firstName"
                label = { Text("Nombres") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Apellidos") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Cuadrito para el Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Cuadrito para la Contraseña (oculta lo que escribes con puntitos)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(), // Esto pone los puntitos de seguridad
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        if (!isLogin) {
            OutlinedTextField(
                value = repeatPassword,
                onValueChange = { repeatPassword = it },
                label = { Text("Repetir contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        /**
         * MENSAJES DE ERROR
         * Si el cerebro (ViewModel) dice que algo salió mal, lo escribimos en color rojo.
         */
        if (uiState.authError != null) {
            Text(
                text = uiState.authError!!,
                color = MaterialTheme.colorScheme.error, // Color de error (rojo)
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        /**
         * EL BOTÓN PRINCIPAL
         */
        Button(
            onClick = {
                // Al hacer clic, revisamos qué queremos hacer
                if (isLogin) {
                    viewModel.login(email, password, onLoginSuccess)
                } else {
                    viewModel.register(firstName, lastName, email, password, repeatPassword, onLoginSuccess)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLogin) "Entrar" else "Registrarse")
        }
        
        /**
         * BOTÓN PARA CAMBIAR DE MODO
         * Si estás en login, te pasa a registro y viceversa.
         */
        TextButton(onClick = { 
            isLogin = !isLogin 
            viewModel.logout {} // Borra errores anteriores para empezar limpio
        }) {
            Text(if (isLogin) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Inicia sesión")
        }
    }
}
