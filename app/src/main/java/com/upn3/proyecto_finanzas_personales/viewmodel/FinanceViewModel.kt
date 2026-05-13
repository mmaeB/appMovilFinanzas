package com.upn3.proyecto_finanzas_personales.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.upn3.proyecto_finanzas_personales.data.UserPreferences
import com.upn3.proyecto_finanzas_personales.model.*
import com.upn3.proyecto_finanzas_personales.ui.theme.AppTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class FinanceState(
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currentUser: User? = null,
    val errorMessage: String? = null,
    val selectedTheme: AppTheme = AppTheme.DEFAULT,
    val isLoading: Boolean = true
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userPreferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(FinanceState())
    val uiState: StateFlow<FinanceState> = _uiState.asStateFlow()

    private val allTransactions = mutableListOf<Transaction>()
    private val allCategories = mutableListOf<Category>()

    init {
        checkSession()
    }

    private fun checkSession() {
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.email != null) {
            viewModelScope.launch {
                try {
                    val userDoc = db.collection("users").document(currentUser.email!!).get().await()
                    if (userDoc.exists()) {
                        val user = userDoc.toObject(User::class.java)
                        if (user != null) {
                            val theme = try { AppTheme.valueOf(user.theme) } catch (e: Exception) { AppTheme.DEFAULT }
                            _uiState.update { it.copy(currentUser = user, selectedTheme = theme, isLoading = false) }
                            loadTransactions()
                            loadCategories()
                        } else {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun selectTheme(theme: AppTheme) {
        _uiState.update { it.copy(selectedTheme = theme) }
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(user.email)
                    .update("theme", theme.name).await()
                _uiState.update { it.copy(currentUser = user.copy(theme = theme.name)) }
            } catch (e: Exception) {
            }
        }
    }

    fun uploadProfilePicture(uri: Uri, onSuccess: (String) -> Unit) {
        val email = uiState.value.currentUser?.email ?: return
        val storageRef = storage.reference.child("profile_pictures/$email.jpg")

        viewModelScope.launch {
            try {
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                onSuccess(downloadUrl)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al subir imagen: ${e.message}") }
            }
        }
    }

    fun updateUser(newName: String, newLastName: String, newEmail: String, newPass: String, newProfilePic: String, onSuccess: () -> Unit) {
        val currentUser = uiState.value.currentUser ?: return
        
        viewModelScope.launch {
            try {
                val updatedUser = currentUser.copy(
                    name = newName,
                    lastname = newLastName,
                    email = newEmail,
                    password = if (newPass.isNotBlank()) newPass else currentUser.password,
                    profilePicture = newProfilePic
                )

                if (newEmail != currentUser.email) {
                    val doc = db.collection("users").document(newEmail).get().await()
                    if (doc.exists()) {
                        _uiState.update { it.copy(errorMessage = "El nuevo correo ya está en uso") }
                        return@launch
                    }
                    db.collection("users").document(newEmail).set(updatedUser).await()
                    db.collection("users").document(currentUser.email).delete().await()
                } else {
                    db.collection("users").document(currentUser.email).set(updatedUser).await()
                }

                _uiState.update { it.copy(currentUser = updatedUser, errorMessage = null) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar perfil: ${e.message}") }
            }
        }
    }

    fun register(
        firstName: String,
        lastName: String,
        email: String,
        pass: String,
        repeatPass: String,
        onSuccess: () -> Unit
    ) {
        if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || pass.isBlank() || repeatPass.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Todos los campos son obligatorios") }
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Correo electrónico no válido") }
            return
        }

        if (pass != repeatPass) {
            _uiState.update { it.copy(errorMessage = "Las contraseñas no coinciden") }
            return
        }

        viewModelScope.launch {
            try {
                auth.createUserWithEmailAndPassword(email, pass).await()
                
                val newUser = User(
                    email = email, 
                    password = pass,
                    name = firstName, 
                    lastname = lastName, 
                    theme = uiState.value.selectedTheme.name,
                    profilePicture = ""
                )
                db.collection("users").document(email).set(newUser).await()
                userPreferences.saveUserEmail(email)
                
                _uiState.update { it.copy(currentUser = newUser, errorMessage = null) }
                loadTransactions()
                loadCategories()
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar: ${e.message}") }
            }
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresa correo y contraseña") }
            return
        }
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, pass).await()
                val userDoc = db.collection("users").document(email).get().await()
                
                if (userDoc.exists()) {
                    val user = userDoc.toObject(User::class.java)
                    if (user != null) {
                        val theme = try { AppTheme.valueOf(user.theme) } catch (e: Exception) { AppTheme.DEFAULT }
                        userPreferences.saveUserEmail(email)
                        _uiState.update { it.copy(currentUser = user, errorMessage = null, selectedTheme = theme) }
                        loadTransactions()
                        loadCategories()
                        onSuccess()
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = "Perfil de usuario no encontrado") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al iniciar sesión: ${e.message}") }
            }
        }
    }

    fun loadTransactions() {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").document(email)
                    .collection("transactions")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await()
                
                val transactions = snapshot.toObjects(Transaction::class.java)
                allTransactions.clear()
                allTransactions.addAll(transactions)
                updateState()
            } catch (e: Exception) {
            }
        }
    }

    fun loadCategories() {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").document(email)
                    .collection("categories")
                    .get().await()
                
                val categories = snapshot.toObjects(Category::class.java)
                allCategories.clear()
                allCategories.addAll(categories)
                
                if (allCategories.isEmpty()) {
                    val defaultCategories = listOf(
                        Category(name = "Salario", type = TransactionType.INCOME),
                        Category(name = "Ventas", type = TransactionType.INCOME),
                        Category(name = "Comida", type = TransactionType.EXPENSE),
                        Category(name = "Transporte", type = TransactionType.EXPENSE),
                        Category(name = "Ocio", type = TransactionType.EXPENSE)
                    )
                    defaultCategories.forEach { addCategory(it) }
                } else {
                    updateState()
                }
            } catch (e: Exception) {
            }
        }
    }

    fun addCategory(category: Category) {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("categories").document(category.id)
                    .set(category).await()
                
                allCategories.add(category)
                updateState()
            } catch (e: Exception) {
            }
        }
    }

    fun deleteCategory(id: String) {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("categories").document(id)
                    .delete().await()
                
                allCategories.removeAll { it.id == id }
                updateState()
            } catch (e: Exception) {
            }
        }
    }

    fun updateTransaction(transaction: Transaction, onSuccess: () -> Unit = {}) {
        val email = uiState.value.currentUser?.email ?: return
        
        val oldTransaction = allTransactions.find { it.id == transaction.id }
        if (oldTransaction != null) {
            val balanceWithoutOld = uiState.value.balance - (if (oldTransaction.type == TransactionType.INCOME) oldTransaction.amount else -oldTransaction.amount)
            val newBalance = balanceWithoutOld + (if (transaction.type == TransactionType.INCOME) transaction.amount else -transaction.amount)
            
            if (newBalance < 0) {
                _uiState.update { it.copy(errorMessage = "Esta modificación resultaría en un saldo negativo (S/.${String.format("%.2f", newBalance)}).") }
                return
            }
        }

        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("transactions").document(transaction.id)
                    .set(transaction).await()
                
                val index = allTransactions.indexOfFirst { it.id == transaction.id }
                if (index != -1) {
                    allTransactions[index] = transaction
                    updateState()
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar transacción") }
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            auth.signOut()
            userPreferences.clearUserEmail()
            allTransactions.clear()
            _uiState.update { it.copy(
                currentUser = null, 
                errorMessage = null, 
                transactions = emptyList(), 
                balance = 0.0,
                selectedTheme = AppTheme.DEFAULT
            ) }
            onSuccess()
        }
    }

    fun deleteTransaction(id: String) {
        val email = uiState.value.currentUser?.email ?: return
        
        val transactionToDelete = allTransactions.find { it.id == id }
        if (transactionToDelete != null) {
            val impact = if (transactionToDelete.type == TransactionType.INCOME) -transactionToDelete.amount else transactionToDelete.amount
            if (uiState.value.balance + impact < 0) {
                _uiState.update { it.copy(errorMessage = "No se puede eliminar: el saldo quedaría en negativo.") }
                return
            }
        }

        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("transactions").document(id)
                    .delete().await()
                
                allTransactions.removeAll { it.id == id }
                updateState()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar transacción") }
            }
        }
    }

    fun addTransaction(amount: Double, description: String, origin: String, type: TransactionType, onSuccess: () -> Unit = {}) {
        val email = uiState.value.currentUser?.email ?: return
        
        if (type == TransactionType.EXPENSE && amount > uiState.value.balance) {
            _uiState.update { it.copy(errorMessage = "Saldo insuficiente. No puedes gastar más de lo que tienes (S/.${String.format("%.2f", uiState.value.balance)}).") }
            return
        }

        val transaction = Transaction(
            amount = amount,
            description = description,
            origin = origin,
            type = type
        )
        
        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("transactions").document(transaction.id)
                    .set(transaction).await()
                
                allTransactions.add(0, transaction)
                updateState()
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al guardar transacción") }
            }
        }
    }

    fun updateBalance(newBalance: Double) {
        allTransactions.clear()
        addTransaction(newBalance, "Ajuste de Saldo", "Sistema", TransactionType.INCOME)
    }

    private fun updateState() {
        val balance = allTransactions.sumOf { 
            if (it.type == TransactionType.INCOME) it.amount else -it.amount 
        }
        _uiState.update { it.copy(
            transactions = allTransactions.toList(), 
            categories = allCategories.toList(),
            balance = balance
        ) }
    }
}
