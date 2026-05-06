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

data class FinanceState(
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val currentUser: User? = null,
    val authError: String? = null
)

interface UserApiService {
    @GET("Usuarios")
    suspend fun getUsers(): List<UserRemote>

    @POST("Usuarios")
    suspend fun createUser(@Body user: UserRemote): UserRemote
}

data class UserRemote(
    val id: String? = null,
    val name: String? = "",
    val lastname: String? = "",
    val email: String? = "",
    val password: String? = ""
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://69d7aba09c5ebb0918c826c0.mockapi.io/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(UserApiService::class.java)

    private val _uiState = MutableStateFlow(FinanceState())
    val uiState: StateFlow<FinanceState> = _uiState.asStateFlow()

    // Como quitamos la base de datos, mantendremos las transacciones en memoria por ahora
    private val allTransactions = mutableListOf<Transaction>()

    fun register(
        firstName: String,
        lastName: String,
        email: String,
        pass: String,
        repeatPass: String,
        onSuccess: () -> Unit
    ) {
        if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || pass.isBlank() || repeatPass.isBlank()) {
            _uiState.update { it.copy(authError = "Todos los campos son obligatorios") }
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(authError = "Correo electrónico no válido") }
            return
        }


        if (pass != repeatPass) {
            _uiState.update { it.copy(authError = "Las contraseñas no coinciden") }
            return
        }

        viewModelScope.launch {
            try {
                val remoteUsers = apiService.getUsers()
                if (remoteUsers.any { it.email == email }) {
                    _uiState.update { it.copy(authError = "El usuario ya existe") }
                } else {
                    val newUserRemote = UserRemote(null, firstName, lastName,
                        email, pass)
                    apiService.createUser(newUserRemote)
                    
                    val newUser = User(email, pass, firstName, lastName)
                    _uiState.update { it.copy(currentUser = newUser, authError = null) }
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(authError = "Error de red: ${e.message}") }
            }
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(authError = "Ingresa correo y contraseña") }
            return
        }
        viewModelScope.launch {
            try {
                val remoteUsers = apiService.getUsers()
                val remoteUser = remoteUsers.find { it.email == email && it.password == pass }
                
                if (remoteUser != null) {
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

    fun logout(onSuccess: () -> Unit) {
        allTransactions.clear()
        _uiState.update { it.copy(currentUser = null, authError = null, transactions = emptyList(), balance = 0.0) }
        onSuccess()
    }

    fun deleteTransaction(id: String) {
        allTransactions.removeAll { it.id == id }
        updateState()
    }

    fun addTransaction(amount: Double, description: String, origin: String, type: TransactionType) {
        val transaction = Transaction(
            amount = amount,
            description = description,
            origin = origin,
            type = type
        )
        allTransactions.add(0, transaction)
        updateState()
    }

    fun updateBalance(newBalance: Double) {
        allTransactions.clear()
        addTransaction(newBalance, "Ajuste de Saldo", "Sistema", TransactionType.INCOME)
    }

    private fun updateState() {
        val balance = allTransactions.sumOf { 
            if (it.type == TransactionType.INCOME) it.amount else -it.amount 
        }
        _uiState.update { it.copy(transactions = allTransactions.toList(), balance = balance) }
    }
}
