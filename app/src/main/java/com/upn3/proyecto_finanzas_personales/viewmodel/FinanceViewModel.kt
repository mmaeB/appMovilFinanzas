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
import kotlinx.coroutines.withTimeout

import com.upn3.proyecto_finanzas_personales.network.CurrencyService
import com.upn3.proyecto_finanzas_personales.network.CurrencyResponse
import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class FinanceState(
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val selectedWallet: Wallet? = null,
    val currentUser: User? = null,
    val errorMessage: String? = null,
    val selectedTheme: AppTheme = AppTheme.DEFAULT,
    val isLoading: Boolean = true,
    val exchangeRatePreview: Double? = null,
    val globalBalance: Double = 0.0,
    val preferredCurrency: String = "PEN",
    val convertedBalance: Double? = null,
    val lastRatesUpdate: Long = 0L
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userPreferences = UserPreferences(application)

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://open.er-api.com/v6/latest/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val currencyService = retrofit.create(CurrencyService::class.java)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(FinanceState())
    val uiState: StateFlow<FinanceState> = _uiState.asStateFlow()

    private val allTransactions = mutableListOf<Transaction>()
    private val allCategories = mutableListOf<Category>()
    private val allWallets = mutableListOf<Wallet>()

    init {
        checkSession()
        observeLastUpdate()
    }

    private fun observeLastUpdate() {
        viewModelScope.launch {
            userPreferences.lastRatesUpdate.collect { timestamp ->
                _uiState.update { it.copy(lastRatesUpdate = timestamp) }
            }
        }
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
                            loadWallets()
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

    fun selectWallet(wallet: Wallet) {
        _uiState.update { it.copy(selectedWallet = wallet) }
        calculateGlobalBalance()
        updateState()
    }

    fun setPreferredCurrency(currencyCode: String) {
        _uiState.update { it.copy(preferredCurrency = currencyCode) }
        calculateGlobalBalance()
    }

    private fun calculateGlobalBalance() {
        val state = _uiState.value
        val wallets = allWallets
        val targetCurrency = state.preferredCurrency

        if (wallets.isEmpty()) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                var rates: Map<String, Double> = emptyMap()
                
                try {
                    // Intentar obtener de la API
                    val response = withTimeout(5000) {
                        currencyService.getCurrencyRate(targetCurrency)
                    }
                    val fetchedRates = response.rates
                    if (fetchedRates != null) {
                        rates = fetchedRates
                        // Guardar en caché
                        val timestamp = System.currentTimeMillis()
                        userPreferences.saveRates(gson.toJson(fetchedRates), timestamp)
                    }
                } catch (e: Exception) {
                    // Si falla la API, intentar usar el caché
                    val cachedJson = userPreferences.cachedRates.first()
                    if (cachedJson != null) {
                        rates = gson.fromJson(cachedJson, Map::class.java) as Map<String, Double>
                    }
                }
                
                var total = 0.0
                for (wallet in wallets) {
                    if (wallet.currencyCode == targetCurrency) {
                        total += wallet.balance
                    } else {
                        val rateToTarget = (rates[wallet.currencyCode] as? Double) ?: 1.0
                        total += wallet.balance / rateToTarget
                    }
                }
                _uiState.update { it.copy(globalBalance = total, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadWallets() {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").document(email)
                    .collection("wallets")
                    .get().await()
                
                val wallets = snapshot.toObjects(Wallet::class.java)
                allWallets.clear()
                allWallets.addAll(wallets)
                
                if (allWallets.isEmpty()) {
                    val defaultWallet = Wallet(
                        id = "default",
                        name = "Billetera Principal",
                        currencyCode = "PEN",
                        balance = 0.0
                    )
                    createWallet(defaultWallet)
                } else {
                    if (_uiState.value.selectedWallet == null) {
                        _uiState.update { it.copy(selectedWallet = allWallets.first()) }
                    }
                    updateState()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al cargar billeteras: ${e.message}") }
            }
        }
    }

    fun createWallet(wallet: Wallet) {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("wallets").document(wallet.id)
                    .set(wallet).await()
                
                if (!allWallets.any { it.id == wallet.id }) {
                    allWallets.add(wallet)
                }
                if (_uiState.value.selectedWallet == null) {
                    _uiState.update { it.copy(selectedWallet = wallet) }
                }
                updateState()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al crear billetera: ${e.message}") }
            }
        }
    }

    fun updateWallet(wallet: Wallet) {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("wallets").document(wallet.id)
                    .set(wallet).await()
                
                val index = allWallets.indexOfFirst { it.id == wallet.id }
                if (index != -1) {
                    allWallets[index] = wallet
                }
                updateState()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar billetera") }
            }
        }
    }

    fun deleteWallet(walletId: String) {
        val email = uiState.value.currentUser?.email ?: return
        if (allWallets.size <= 1) {
            _uiState.update { it.copy(errorMessage = "No puedes eliminar tu única billetera") }
            return
        }
        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("wallets").document(walletId)
                    .delete().await()
                
                allWallets.removeAll { it.id == walletId }
                if (_uiState.value.selectedWallet?.id == walletId) {
                    _uiState.update { it.copy(selectedWallet = allWallets.first()) }
                }
                updateState()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar billetera: ${e.message}") }
            }
        }
    }

    fun fetchExchangeRatePreview(fromCode: String, toCode: String) {
        if (fromCode == toCode) {
            _uiState.update { it.copy(exchangeRatePreview = 1.0, isLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                var rates: Map<String, Double> = emptyMap()
                
                try {
                    // Usar la misma lógica que Resumen Global: base = toCode
                    val response = withTimeout(5000) {
                        currencyService.getCurrencyRate(toCode)
                    }
                    val fetchedRates = response.rates
                    if (fetchedRates != null) {
                        rates = fetchedRates
                        // Guardamos en el mismo caché que usa Resumen Global
                        userPreferences.saveRates(gson.toJson(fetchedRates), System.currentTimeMillis())
                    }
                } catch (e: Exception) {
                    // Si falla la API, intentar usar el caché que usa Resumen Global
                    val cachedJson = userPreferences.cachedRates.first()
                    if (cachedJson != null) {
                        // Usamos TypeToken de GSON directamente
                        val type = object : com.google.gson.reflect.TypeToken<Map<String, Double>>() {}.type
                        rates = gson.fromJson(cachedJson, type)
                    }
                }

                // Cálculo inverso igual que en Resumen Global
                val rateValue = rates[fromCode]
                val rateToSource = when (rateValue) {
                    is Double -> rateValue
                    is Number -> rateValue.toDouble()
                    else -> 1.0
                }
                val multiplier = if (rateToSource != 0.0) 1.0 / rateToSource else 1.0
                
                _uiState.update { it.copy(exchangeRatePreview = multiplier, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        exchangeRatePreview = 1.0, 
                        isLoading = false
                    ) 
                }
            }
        }
    }

    fun clearExchangeRatePreview() {
        _uiState.update { it.copy(exchangeRatePreview = null) }
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
        val wallet = allWallets.find { it.id == transaction.walletId }
        
        if (oldTransaction != null && wallet != null) {
            val balanceWithoutOld = wallet.balance - (if (oldTransaction.type == TransactionType.INCOME) oldTransaction.amount else -oldTransaction.amount)
            val newBalance = balanceWithoutOld + (if (transaction.type == TransactionType.INCOME) transaction.amount else -transaction.amount)
            
            if (newBalance < 0) {
                _uiState.update { it.copy(errorMessage = "Esta modificación resultaría en un saldo negativo en la billetera ${wallet.name}.") }
                return
            }
        }

        viewModelScope.launch {
            try {
                db.runBatch { batch ->
                    val transRef = db.collection("users").document(email)
                        .collection("transactions").document(transaction.id)
                    batch.set(transRef, transaction)
                    
                    if (oldTransaction != null && wallet != null) {
                        val diff = (if (transaction.type == TransactionType.INCOME) transaction.amount else -transaction.amount) -
                                   (if (oldTransaction.type == TransactionType.INCOME) oldTransaction.amount else -oldTransaction.amount)
                        
                        if (diff != 0.0) {
                            val walletRef = db.collection("users").document(email)
                                .collection("wallets").document(wallet.id)
                            batch.update(walletRef, "balance", wallet.balance + diff)
                        }
                    }
                }.await()
                
                val index = allTransactions.indexOfFirst { it.id == transaction.id }
                if (index != -1) {
                    allTransactions[index] = transaction
                }
                
                if (oldTransaction != null && wallet != null) {
                    val diff = (if (transaction.type == TransactionType.INCOME) transaction.amount else -transaction.amount) -
                               (if (oldTransaction.type == TransactionType.INCOME) oldTransaction.amount else -oldTransaction.amount)
                    val wIndex = allWallets.indexOfFirst { it.id == wallet.id }
                    if (wIndex != -1) {
                        allWallets[wIndex] = allWallets[wIndex].copy(balance = allWallets[wIndex].balance + diff)
                    }
                }
                
                updateState()
                onSuccess()
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
        val wallet = allWallets.find { it.id == transactionToDelete?.walletId }
        
        if (transactionToDelete != null && wallet != null) {
            val impact = if (transactionToDelete.type == TransactionType.INCOME) -transactionToDelete.amount else transactionToDelete.amount
            if (wallet.balance + impact < 0) {
                _uiState.update { it.copy(errorMessage = "No se puede eliminar: el saldo de la billetera ${wallet.name} quedaría en negativo.") }
                return
            }
        }

        viewModelScope.launch {
            try {
                db.runBatch { batch ->
                    val transRef = db.collection("users").document(email)
                        .collection("transactions").document(id)
                    batch.delete(transRef)
                    
                    if (transactionToDelete != null && wallet != null) {
                        val impact = if (transactionToDelete.type == TransactionType.INCOME) -transactionToDelete.amount else transactionToDelete.amount
                        val walletRef = db.collection("users").document(email)
                            .collection("wallets").document(wallet.id)
                        batch.update(walletRef, "balance", wallet.balance + impact)
                    }
                }.await()
                
                allTransactions.removeAll { it.id == id }
                if (transactionToDelete != null && wallet != null) {
                    val impact = if (transactionToDelete.type == TransactionType.INCOME) -transactionToDelete.amount else transactionToDelete.amount
                    val wIndex = allWallets.indexOfFirst { it.id == wallet.id }
                    if (wIndex != -1) {
                        allWallets[wIndex] = allWallets[wIndex].copy(balance = allWallets[wIndex].balance + impact)
                    }
                }
                updateState()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar transacción") }
            }
        }
    }

    fun addTransaction(amount: Double, description: String, origin: String, type: TransactionType, walletId: String? = null, onSuccess: () -> Unit = {}) {
        val email = uiState.value.currentUser?.email ?: return
        val targetWalletId = walletId ?: _uiState.value.selectedWallet?.id ?: "default"
        val wallet = allWallets.find { it.id == targetWalletId }

        if (wallet == null) {
            _uiState.update { it.copy(errorMessage = "Billetera no encontrada") }
            return
        }

        if (type == TransactionType.EXPENSE && amount > wallet.balance) {
            _uiState.update { it.copy(errorMessage = "Saldo insuficiente en ${wallet.name}. (Saldo: ${wallet.currencyCode} ${String.format("%.2f", wallet.balance)})") }
            return
        }

        val transaction = Transaction(
            amount = amount,
            description = description,
            origin = origin,
            type = type,
            walletId = targetWalletId
        )
        
        viewModelScope.launch {
            try {
                db.runBatch { batch ->
                    val transRef = db.collection("users").document(email)
                        .collection("transactions").document(transaction.id)
                    batch.set(transRef, transaction)
                    
                    val walletRef = db.collection("users").document(email)
                        .collection("wallets").document(targetWalletId)
                    val newBalance = if (type == TransactionType.INCOME) wallet.balance + amount else wallet.balance - amount
                    batch.update(walletRef, "balance", newBalance)
                }.await()
                
                allTransactions.add(0, transaction)
                val wIndex = allWallets.indexOfFirst { it.id == targetWalletId }
                if (wIndex != -1) {
                    allWallets[wIndex] = allWallets[wIndex].copy(balance = if (type == TransactionType.INCOME) allWallets[wIndex].balance + amount else allWallets[wIndex].balance - amount)
                }
                updateState()
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al guardar transacción") }
            }
        }
    }

    fun transferMoney(fromWallet: Wallet, toWallet: Wallet, amount: Double, onSuccess: () -> Unit = {}) {
        val email = uiState.value.currentUser?.email ?: return
        
        if (amount > fromWallet.balance) {
            _uiState.update { it.copy(errorMessage = "Saldo insuficiente en ${fromWallet.name}") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                var conversionRate = 1.0
                if (fromWallet.currencyCode != toWallet.currencyCode) {
                    var rates: Map<String, Double> = emptyMap()
                    try {
                        // Usar la misma lógica que Resumen Global: base = Moneda Destino (toWallet)
                        val response = withTimeout(5000) {
                            currencyService.getCurrencyRate(toWallet.currencyCode)
                        }
                        rates = response.rates ?: emptyMap()
                        userPreferences.saveRates(gson.toJson(rates), System.currentTimeMillis())
                    } catch (e: Exception) {
                        val cachedJson = userPreferences.cachedRates.first()
                        if (cachedJson != null) {
                            val type = object : com.google.gson.reflect.TypeToken<Map<String, Double>>() {}.type
                            rates = gson.fromJson(cachedJson, type)
                        }
                    }
                    
                    val rateValue = rates[fromWallet.currencyCode]
                    val rateToSource = when (rateValue) {
                        is Double -> rateValue
                        is Number -> rateValue.toDouble()
                        else -> 1.0
                    }
                    conversionRate = if (rateToSource != 0.0) 1.0 / rateToSource else 1.0
                }

                val convertedAmount = amount * conversionRate

                val expenseTrans = Transaction(
                    amount = amount,
                    description = "Transferencia a ${toWallet.name}",
                    origin = "Transferencia",
                    type = TransactionType.EXPENSE,
                    walletId = fromWallet.id
                )

                val incomeTrans = Transaction(
                    amount = convertedAmount,
                    description = "Transferencia desde ${fromWallet.name}",
                    origin = "Transferencia",
                    type = TransactionType.INCOME,
                    walletId = toWallet.id
                )

                db.runBatch { batch ->
                    val userRef = db.collection("users").document(email)
                    
                    batch.set(userRef.collection("transactions").document(expenseTrans.id), expenseTrans)
                    batch.set(userRef.collection("transactions").document(incomeTrans.id), incomeTrans)
                    
                    batch.update(userRef.collection("wallets").document(fromWallet.id), "balance", fromWallet.balance - amount)
                    batch.update(userRef.collection("wallets").document(toWallet.id), "balance", toWallet.balance + convertedAmount)
                }.await()

                allTransactions.add(0, expenseTrans)
                allTransactions.add(0, incomeTrans)
                
                val fIdx = allWallets.indexOfFirst { it.id == fromWallet.id }
                if (fIdx != -1) allWallets[fIdx] = allWallets[fIdx].copy(balance = allWallets[fIdx].balance - amount)
                
                val tIdx = allWallets.indexOfFirst { it.id == toWallet.id }
                if (tIdx != -1) allWallets[tIdx] = allWallets[tIdx].copy(balance = allWallets[tIdx].balance + convertedAmount)

                updateState()
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error en la transferencia: ${e.message}") }
            }
        }
    }

    fun adjustBalance(newBalance: Double) {
        val currentBalance = uiState.value.balance
        val diff = newBalance - currentBalance
        if (diff == 0.0) return

        val type = if (diff > 0) TransactionType.INCOME else TransactionType.EXPENSE
        addTransaction(kotlin.math.abs(diff), "Ajuste de Saldo", "Sistema", type)
    }

    fun resetTransactions(initialBalance: Double) {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                val transactionsRef = db.collection("users").document(email).collection("transactions")
                val snapshot = transactionsRef.get().await()
                
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
                
                allTransactions.clear()
                
                if (initialBalance > 0) {
                    val transaction = Transaction(
                        amount = initialBalance,
                        description = "Reinicio de Saldo",
                        origin = "Sistema",
                        type = TransactionType.INCOME
                    )
                    transactionsRef.document(transaction.id).set(transaction).await()
                    allTransactions.add(transaction)
                }
                updateState()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al reiniciar: ${e.message}") }
            }
        }
    }

    private fun updateState() {
        val selectedWallet = _uiState.value.selectedWallet
        val filteredTransactions = if (selectedWallet != null) {
            allTransactions.filter { it.walletId == selectedWallet.id }
        } else {
            allTransactions
        }
        
        val balance = selectedWallet?.balance ?: allWallets.sumOf { it.balance }

        _uiState.update { it.copy(
            transactions = filteredTransactions,
            categories = allCategories.toList(),
            wallets = allWallets.toList(),
            selectedWallet = allWallets.find { w -> w.id == selectedWallet?.id } ?: allWallets.firstOrNull(),
            balance = balance
        ) }
        calculateGlobalBalance()
    }
}
