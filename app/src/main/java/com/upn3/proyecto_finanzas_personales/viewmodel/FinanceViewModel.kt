package com.upn3.proyecto_finanzas_personales.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.upn3.proyecto_finanzas_personales.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

/**
 * FinanceState: Representa el estado global de la interfaz de usuario (UI).
 * @param balance El saldo total calculado del usuario.
 * @param transactions Lista de transacciones (Ingresos/Gastos) a mostrar.
 * @param currentUser Datos del usuario que ha iniciado sesión.
 * @param authError Mensaje de error en caso de que falle el login o registro.
 */
data class FinanceState(
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val currentUser: User? = null,
    val authError: String? = null
)

/**
 * UserApiService: Define las operaciones que podemos hacer con la API externa (Retrofit).
 * Aquí se configuran los "EndPoints" de tu servicio MockAPI.
 */
interface UserApiService {
    // Obtiene la lista de todos los usuarios registrados en la nube
    @GET("Usuarios")
    suspend fun getUsers(): List<UserRemote>

    // Envía un nuevo usuario a la nube para registrarlo
    @POST("Usuarios")
    suspend fun createUser(@Body user: UserRemote): UserRemote
}

/**
 * UserRemote: Modelo de datos exacto como viene de la API.
 * Es importante que los nombres coincidan con el JSON de la API.
 */
data class UserRemote(
    val id: String? = null,
    val name: String? = "",
    val lastname: String? = "",
    val email: String? = "",
    val password: String? = ""
)

/**
 * FinanceViewModel: El cerebro de la aplicación.
 * Maneja la lógica de negocio, se comunica con la API y expone el estado a la UI.
 */
class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    
    // Configuración de Retrofit para conectar con la API
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://69d7aba09c5a@gmail.comebb0918c826c0.mockapi.io/") // URL base de tu API
        .addConverterFactory(GsonConverterFactory.create()) // Convierte el JSON a objetos Kotlin
        .build()

    // Crea la implementación de las funciones de la API
    private val apiService = retrofit.create(UserApiService::class.java)

    // Estado interno (Mutable) que solo el ViewModel puede cambiar
    private val _uiState = MutableStateFlow(FinanceState())
    // Estado público (Solo lectura) que la UI observa para redibujarse
    val uiState: StateFlow<FinanceState> = _uiState.asStateFlow()

    // Lista temporal en memoria para las transacciones (se pierden al cerrar la app si no hay DB)
    private val allTransactions = mutableListOf<Transaction>()

    /**
     * Función para registrar un nuevo usuario.
     * Valida campos, revisa si el correo ya existe en la API y crea el perfil.
     */
    fun register(
        firstName: String,
        lastName: String,
        email: String,
        pass: String,
        repeatPass: String,
        onSuccess: () -> Unit
    ) {
        // Validación de campos vacíos
        if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || pass.isBlank() || repeatPass.isBlank()) {
            _uiState.update { it.copy(authError = "Todos los campos son obligatorios") }
            return
        }

        // Validación de formato de correo
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(authError = "Correo electrónico no válido") }
            return
        }

        // Validación de coincidencia de contraseñas
        if (pass != repeatPass) {
            _uiState.update { it.copy(authError = "Las contraseñas no coinciden") }
            return
        }

        // Ejecución en segundo plano (Corrutina) para no congelar la pantalla
        viewModelScope.launch {
            try {
                val remoteUsers = apiService.getUsers()
                // Verificamos si el email ya está registrado
                if (remoteUsers.any { it.email == email }) {
                    _uiState.update { it.copy(authError = "El usuario ya existe") }
                } else {
                    // Si es nuevo, lo mandamos a la API
                    val newUserRemote = UserRemote(null, firstName, lastName, email, pass)
                    apiService.createUser(newUserRemote)
                    
                    // Actualizamos el estado local con el nuevo usuario
                    val newUser = User(email, pass, firstName, lastName)
                    _uiState.update { it.copy(currentUser = newUser, authError = null) }
                    onSuccess() // Navega al Dashboard
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(authError = "Error de red: ${e.message}") }
            }
        }
    }

    /**
     * Función para iniciar sesión.
     * Busca en la API un usuario que coincida con email y contraseña.
     */
    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(authError = "Ingresa correo y contraseña") }
            return
        }
        viewModelScope.launch {
            try {
                val remoteUsers = apiService.getUsers()
                // Buscamos coincidencia exacta
                val remoteUser = remoteUsers.find { it.email == email && it.password == pass }
                
                if (remoteUser != null) {
                    // Mapeamos de UserRemote (API) a User (App)
                    val user = User(
                        remoteUser.email ?: "",
                        remoteUser.password ?: "",
                        remoteUser.name ?: "",
                        remoteUser.lastname ?: ""
                    )
                    _uiState.update { it.copy(currentUser = user, authError = null) }
                    onSuccess()
                } else {
                    _uiState.update { it.copy(authError = "Credenciales incorrectas") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(authError = "Error al conectar: ${e.message}") }
            }
        }
    }

    /**
     * Limpia la sesión y los datos temporales.
     */
    fun logout(onSuccess: () -> Unit) {
        allTransactions.clear()
        _uiState.update { it.copy(currentUser = null, authError = null, transactions = emptyList(), balance = 0.0) }
        onSuccess()
    }

    /**
     * Elimina una transacción de la lista por su ID.
     */
    fun deleteTransaction(id: String) {
        allTransactions.removeAll { it.id == id }
        updateState() // Refresca el saldo y la lista
    }

    /**
     * Añade una nueva transacción (Gasto o Ingreso).
     */
    fun addTransaction(amount: Double, description: String, origin: String, type: TransactionType) {
        if (amount > 0 || description.isBlank() || origin.isBlank() ) {
            _uiState.update { it.copy(authError = "Todos los campos son obligatorios") }
            return
        }

        val transaction = Transaction(
            amount = amount,
            description = description,
            origin = origin,
            type = type
        )
        allTransactions.add(0, transaction) // Añade al inicio de la lista
        updateState()


    }

    /**
     * Permite ajustar el saldo inicial o total.
     */
    fun updateBalance(newBalance: Double) {
        allTransactions.clear()
        addTransaction(newBalance, "Ajuste de Saldo", "Sistema", TransactionType.INCOME)


    }

    /**
     * Recalcula el saldo total sumando ingresos y restando gastos.
     * Luego actualiza el UI State para que la pantalla se refresque.
     */
    private fun updateState() {
        val balance = allTransactions.sumOf { 
            if (it.type == TransactionType.INCOME) it.amount else -it.amount 
        }
        _uiState.update { it.copy(transactions = allTransactions.toList(), balance = balance) }

    }
}
