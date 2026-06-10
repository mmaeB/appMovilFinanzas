package com.upn3.proyecto_finanzas_personales.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.upn3.proyecto_finanzas_personales.data.UserPreferences
import com.upn3.proyecto_finanzas_personales.model.*
import com.upn3.proyecto_finanzas_personales.ui.theme.AppTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import com.upn3.proyecto_finanzas_personales.network.CurrencyService
import com.upn3.proyecto_finanzas_personales.network.CurrencyResponse
import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.content.Context
import com.upn3.proyecto_finanzas_personales.network.CloudinaryClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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
    val isExchangeLoading: Boolean = false,
    val exchangeRatePreview: Double? = null,
    val globalBalance: Double = 0.0,
    val preferredCurrency: String = "PEN",
    val convertedBalance: Double? = null,
    val lastRatesUpdate: Long = 0L
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val db = FirebaseFirestore.getInstance()
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

    fun uploadProfilePicture(
        context: Context,
        uri: Uri,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val inputStream =
                    context.contentResolver.openInputStream(uri)

                val tempFile =
                    File.createTempFile(
                        "profile_picture",
                        ".jpg"
                    )

                tempFile.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }

                val requestFile =
                    tempFile.asRequestBody(
                        "image/*".toMediaType()
                    )

                val imagePart =
                    MultipartBody.Part.createFormData(
                        "file",
                        tempFile.name,
                        requestFile
                    )

                val preset =
                    "profile_images"
                        .toRequestBody(
                            "text/plain".toMediaType()
                        )

                val response =
                    CloudinaryClient.api.uploadImage(
                        imagePart,
                        preset
                    )

                onSuccess(response.secureUrl)

            } catch (e: Exception) {

                _uiState.update {
                    it.copy(
                        errorMessage =
                            "Error al subir imagen: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateUser(newName: String, newLastName: String, newEmail: String, newPass: String, newProfilePic: String, onSuccess: () -> Unit) {
        val currentUser = uiState.value.currentUser ?: return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Intentar actualizar en Firebase Auth si cambió la contraseña o el correo
                if (newPass.isNotBlank() && newPass != currentUser.password) {
                    auth.currentUser?.updatePassword(newPass)?.await()
                }
                
                if (newEmail != currentUser.email) {
                    auth.currentUser?.updateEmail(newEmail)?.await()
                }

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

                _uiState.update { it.copy(currentUser = updatedUser, errorMessage = null, isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar perfil: ${e.message}", isLoading = false) }
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
                loadWallets()
                loadTransactions()
                loadCategories()
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar: ${e.message}") }
            }
        }
    }

    // --- SECCIÓN DE IMPORTACIÓN Y EXPORTACIÓN CSV CON CIFRADO OPCIONAL ---

    /**
     * Exporta todas las transacciones a un archivo CSV.
     * Si se proporciona una contraseña, se cifra el contenido usando AES.
     * Comentario: Se utiliza PBKDF2 para generar la clave a partir de la contraseña
     * y AES/CBC/PKCS5Padding para el cifrado seguro.
     */
    fun exportTransactionsToCsv(uri: Uri, password: String?, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Generar contenido CSV básico
                val csvHeader = "id,walletId,amount,description,origin,type,timestamp\n"
                val csvData = allTransactions.joinToString("\n") { 
                    "${it.id},${it.walletId},${it.amount},${it.description.replace(",", ";")},${it.origin},${it.type},${it.timestamp}"
                }
                val fullCsv = csvHeader + csvData
                
                // Cifrar si hay contraseña
                val finalData = if (!password.isNullOrBlank()) {
                    encryptData(fullCsv, password)
                } else {
                    fullCsv
                }
                
                // Escribir en el archivo seleccionado por el usuario
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(finalData.toByteArray())
                }
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "Exportación exitosa en el archivo seleccionado") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "Error al exportar: ${e.message}") }
                }
            }
        }
    }

    /**
     * Importa transacciones desde un archivo CSV.
     * Soporta archivos planos o cifrados (si se provee la contraseña correcta).
     * Comentario: Lee el archivo como texto, intenta descifrar si es necesario,
     * y luego parsea línea por línea para subir los datos a Firestore mediante un Batch.
     */
    fun importTransactionsFromCsv(uri: Uri, password: String?, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@launch
                
                // Descifrar si hay contraseña
                val decryptedContent = if (!password.isNullOrBlank()) {
                    try {
                        decryptData(content, password)
                    } catch (e: Exception) {
                        throw Exception("Contraseña incorrecta o formato de archivo inválido")
                    }
                } else {
                    content
                }
                
                val lines = decryptedContent.lines()
                if (lines.isEmpty()) return@launch
                
                val transactions = mutableListOf<Transaction>()
                // Omitir encabezado e iterar
                lines.drop(1).forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split(",")
                    if (parts.size >= 7) {
                        transactions.add(Transaction(
                            id = parts[0],
                            walletId = parts[1],
                            amount = parts[2].toDoubleOrNull() ?: 0.0,
                            description = parts[3].replace(";", ","),
                            origin = parts[4],
                            type = try { TransactionType.valueOf(parts[5]) } catch(e:Exception) { TransactionType.EXPENSE },
                            timestamp = parts[6].toLongOrNull() ?: System.currentTimeMillis()
                        ))
                    }
                }
                
                if (transactions.isEmpty()) throw Exception("No se encontraron transacciones válidas")

                // Subir a Firestore en lotes (batch) para mayor eficiencia
                val userEmail = uiState.value.currentUser?.email ?: return@launch
                transactions.chunked(500).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { transaction ->
                        val docRef = db.collection("users").document(userEmail)
                            .collection("transactions").document(transaction.id)
                        batch.set(docRef, transaction)
                    }
                    batch.commit().await()
                }
                
                // Actualizar la UI local
                loadTransactions()
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "Importación exitosa: ${transactions.size} registros añadidos") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "Error al importar: ${e.message}") }
                }
            }
        }
    }

    // --- FUNCIONES AUXILIARES DE CIFRADO ---

    private fun encryptData(data: String, password: String): String {
        val salt = "finance_app_salt".toByteArray() // Sal fija para el ejemplo
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 128)
        val tmp = factory.generateSecret(spec)
        val secret = SecretKeySpec(tmp.encoded, "AES")
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val params = cipher.parameters
        val iv = params.getParameterSpec(IvParameterSpec::class.java).iv
        val ciphertext = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        // Retornamos IV + Ciphertext en Base64 para poder recuperarlo después
        return Base64.encodeToString(iv + ciphertext, Base64.DEFAULT)
    }

    private fun decryptData(encryptedData: String, password: String): String {
        val combined = Base64.decode(encryptedData, Base64.DEFAULT)
        val iv = combined.sliceArray(0 until 16)
        val ciphertext = combined.sliceArray(16 until combined.size)
        
        val salt = "finance_app_salt".toByteArray()
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 128)
        val tmp = factory.generateSecret(spec)
        val secret = SecretKeySpec(tmp.encoded, "AES")
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
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
                        loadWallets()
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
                        val rateValue = rates[wallet.currencyCode]
                        val rateToTarget = when (rateValue) {
                            is Double -> rateValue
                            is Number -> rateValue.toDouble()
                            else -> 1.0
                        }
                        // La tasa obtenida de la API suele ser 1 moneda_base = X target_currency.
                        // Si la API devuelve tasas basadas en USD y target es USD, rates["PEN"] es PEN/USD.
                        // Sin embargo, si usamos currencyService.getCurrencyRate(targetCurrency),
                        // lo más probable es que devuelva tasas donde 1 targetCurrency = X otherCurrency.
                        // Por lo tanto, para convertir a targetCurrency: wallet.balance / rateToOther.
                        total += if (rateToTarget != 0.0) wallet.balance / rateToTarget else wallet.balance
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
        val oldWallet = allWallets.find { it.id == wallet.id }
        
        // Si el saldo llega en 0 pero la billetera original tenía dinero, 
        // y no hubo una conversión explícita exitosa, evitamos el reseteo.
        val finalBalance = if (wallet.balance == 0.0 && (oldWallet?.balance ?: 0.0) > 0.0 && uiState.value.exchangeRatePreview == null) {
            oldWallet?.balance ?: 0.0
        } else {
            wallet.balance
        }

        val walletToSave = wallet.copy(balance = finalBalance)

        viewModelScope.launch {
            try {
                db.collection("users").document(email)
                    .collection("wallets").document(walletToSave.id)
                    .set(walletToSave).await()
                
                val index = allWallets.indexOfFirst { it.id == walletToSave.id }
                if (index != -1) {
                    allWallets[index] = walletToSave
                }

                // Si la moneda cambió, convertir las transacciones existentes al nuevo tipo de cambio
                if (oldWallet != null && oldWallet.currencyCode != walletToSave.currencyCode) {
                    val rate = if (oldWallet.balance != 0.0) walletToSave.balance / oldWallet.balance else 1.0
                    
                    val transactionsRef = db.collection("users").document(email).collection("transactions")
                    val snapshot = transactionsRef.whereEqualTo("walletId", walletToSave.id).get().await()
                    
                    if (!snapshot.isEmpty) {
                        db.runBatch { batch ->
                            snapshot.documents.forEach { doc ->
                                val oldAmt = doc.getDouble("amount") ?: 0.0
                                val origin = doc.getString("origin") ?: ""
                                
                                // No convertir transacciones informativas de sistema (monto 0)
                                if (origin == "Ajuste de Moneda" && oldAmt == 0.0) return@forEach
                                
                                batch.update(doc.reference, "amount", oldAmt * rate)
                            }
                        }.await()
                        
                        // Actualizar lista local
                        val updatedLocal = allTransactions.map { 
                            if (it.walletId == walletToSave.id && !(it.origin == "Ajuste de Moneda" && it.amount == 0.0)) {
                                it.copy(amount = it.amount * rate)
                            } else it
                        }
                        allTransactions.clear()
                        allTransactions.addAll(updatedLocal)
                    }

                    // Registrar el evento informativo de cambio de moneda (monto 0 para no duplicar balance)
                    val event = Transaction(
                        amount = 0.0,
                        description = "Ajuste de Saldo (Cambio de Moneda: ${oldWallet.currencyCode} -> ${walletToSave.currencyCode})",
                        origin = "Ajuste de Moneda",
                        type = TransactionType.INCOME,
                        walletId = walletToSave.id,
                        timestamp = System.currentTimeMillis()
                    )
                    db.collection("users").document(email).collection("transactions").document(event.id).set(event).await()
                    allTransactions.add(0, event)
                }

                updateState()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar billetera") }
            }
        }
    }

    fun deleteWallet(walletId: String) {
        val email = uiState.value.currentUser?.email ?: return
        viewModelScope.launch {
            try {
                // Borrar la billetera de Firestore
                db.collection("users").document(email)
                    .collection("wallets").document(walletId)
                    .delete().await()
                
                // Borrar las transacciones asociadas a esta billetera
                val transactionsQuery = db.collection("users").document(email)
                    .collection("transactions").whereEqualTo("walletId", walletId).get().await()
                
                if (!transactionsQuery.isEmpty) {
                    db.runBatch { batch ->
                        transactionsQuery.documents.forEach { doc ->
                            batch.delete(doc.reference)
                        }
                    }.await()
                }
                
                // Actualizar listas locales
                allWallets.removeAll { it.id == walletId }
                allTransactions.removeAll { it.walletId == walletId }
                
                if (_uiState.value.selectedWallet?.id == walletId) {
                    _uiState.update { it.copy(selectedWallet = allWallets.firstOrNull()) }
                }
                updateState()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar billetera: ${e.message}") }
            }
        }
    }

    fun fetchExchangeRatePreview(fromCode: String, toCode: String) {
        if (fromCode == toCode) {
            _uiState.update { it.copy(exchangeRatePreview = 1.0, isExchangeLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isExchangeLoading = true) }
                
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
                
                _uiState.update { it.copy(exchangeRatePreview = multiplier, isExchangeLoading = false) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        exchangeRatePreview = 1.0, 
                        isExchangeLoading = false
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
        
        // Determinar si esta transacción debe afectar el balance
        val isAdjustment = transaction.origin == "Sistema"
        
        if (oldTransaction != null && wallet != null && !isAdjustment) {
            val balanceWithoutOld = wallet.balance - when(oldTransaction.type) {
                TransactionType.INCOME -> oldTransaction.amount
                TransactionType.EXPENSE, TransactionType.TRANSFER -> -oldTransaction.amount
            }
            val newBalance = balanceWithoutOld + when(transaction.type) {
                TransactionType.INCOME -> transaction.amount
                TransactionType.EXPENSE, TransactionType.TRANSFER -> -transaction.amount
            }
            
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
                    
                    if (oldTransaction != null && wallet != null && !isAdjustment) {
                        val oldImpact = when(oldTransaction.type) {
                            TransactionType.INCOME -> oldTransaction.amount
                            TransactionType.EXPENSE, TransactionType.TRANSFER -> -oldTransaction.amount
                        }
                        val newImpact = when(transaction.type) {
                            TransactionType.INCOME -> transaction.amount
                            TransactionType.EXPENSE, TransactionType.TRANSFER -> -transaction.amount
                        }
                        val diff = newImpact - oldImpact
                        
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
                
                if (oldTransaction != null && wallet != null && !isAdjustment) {
                    val oldImpact = when(oldTransaction.type) {
                        TransactionType.INCOME -> oldTransaction.amount
                        TransactionType.EXPENSE, TransactionType.TRANSFER -> -oldTransaction.amount
                    }
                    val newImpact = when(transaction.type) {
                        TransactionType.INCOME -> transaction.amount
                        TransactionType.EXPENSE, TransactionType.TRANSFER -> -transaction.amount
                    }
                    val diff = newImpact - oldImpact
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
        val isAdjustment = transactionToDelete?.origin == "Sistema"

        viewModelScope.launch {
            try {
                db.runBatch { batch ->
                    val transRef = db.collection("users").document(email)
                        .collection("transactions").document(id)
                    batch.delete(transRef)
                    
                    if (transactionToDelete != null && wallet != null && !isAdjustment) {
                        val impact = when(transactionToDelete.type) {
                            TransactionType.INCOME -> -transactionToDelete.amount
                            TransactionType.EXPENSE, TransactionType.TRANSFER -> transactionToDelete.amount
                        }
                        val walletRef = db.collection("users").document(email)
                            .collection("wallets").document(wallet.id)
                        batch.update(walletRef, "balance", wallet.balance + impact)
                    }
                }.await()
                
                allTransactions.removeAll { it.id == id }
                if (transactionToDelete != null && wallet != null && !isAdjustment) {
                    val impact = when(transactionToDelete.type) {
                        TransactionType.INCOME -> -transactionToDelete.amount
                        TransactionType.EXPENSE, TransactionType.TRANSFER -> transactionToDelete.amount
                    }
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
        addTransactionWithDate(amount, description, origin, type, System.currentTimeMillis(), walletId, onSuccess)
    }

    fun addTransactionWithDate(amount: Double, description: String, origin: String, type: TransactionType, timestamp: Long, walletId: String? = null, onSuccess: () -> Unit = {}) {
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

        if (type == TransactionType.TRANSFER && amount > wallet.balance) {
            _uiState.update { it.copy(errorMessage = "Saldo insuficiente en ${wallet.name} para transferencia.") }
            return
        }

        val transaction = Transaction(
            amount = amount,
            description = description,
            origin = origin,
            type = type,
            walletId = targetWalletId,
            timestamp = timestamp
        )
        
        viewModelScope.launch {
            try {
                db.runBatch { batch ->
                    val transRef = db.collection("users").document(email)
                        .collection("transactions").document(transaction.id)
                    batch.set(transRef, transaction)
                    
                    val walletRef = db.collection("users").document(email)
                        .collection("wallets").document(targetWalletId)
                    val newBalance = when(type) {
                        TransactionType.INCOME -> wallet.balance + amount
                        TransactionType.EXPENSE -> wallet.balance - amount
                        TransactionType.TRANSFER -> wallet.balance - amount
                    }
                    batch.update(walletRef, "balance", newBalance)
                }.await()
                
                allTransactions.add(0, transaction)
                val wIndex = allWallets.indexOfFirst { it.id == targetWalletId }
                if (wIndex != -1) {
                    val currentBal = allWallets[wIndex].balance
                    allWallets[wIndex] = allWallets[wIndex].copy(balance = when(type) {
                        TransactionType.INCOME -> currentBal + amount
                        TransactionType.EXPENSE -> currentBal - amount
                        TransactionType.TRANSFER -> currentBal - amount
                    })
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
                    type = TransactionType.TRANSFER,
                    walletId = fromWallet.id
                )

                val incomeTrans = Transaction(
                    amount = convertedAmount,
                    description = "Transferencia desde ${fromWallet.name}",
                    origin = "Transferencia",
                    type = TransactionType.TRANSFER,
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
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error en la transferencia: ${e.message}", isLoading = false) }
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
                        description = "Ajuste de Saldo",
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

    fun getCurrencySymbol(currencyCode: String): String {
        return when (currencyCode) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "MXN" -> "Mex$"
            "CLP" -> "CLP$"
            "BRL" -> "R$"
            else -> "S/." // Default for PEN
        }
    }

    private fun updateState() {
        val currentSelectedWalletId = _uiState.value.selectedWallet?.id
        val updatedSelectedWallet = allWallets.find { it.id == currentSelectedWalletId } ?: allWallets.firstOrNull()
        
        val filteredTransactions = if (updatedSelectedWallet != null) {
            allTransactions.filter { it.walletId == updatedSelectedWallet.id }
        } else {
            allTransactions
        }
        
        val balance = updatedSelectedWallet?.balance ?: allWallets.sumOf { it.balance }

        _uiState.update { it.copy(
            transactions = filteredTransactions.sortedByDescending { t -> t.timestamp },
            categories = allCategories.toList(),
            wallets = allWallets.toList(),
            selectedWallet = updatedSelectedWallet,
            balance = balance
        ) }
        calculateGlobalBalance()
    }
}
