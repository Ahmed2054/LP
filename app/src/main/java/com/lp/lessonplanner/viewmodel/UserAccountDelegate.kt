package com.lp.lessonplanner.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.lp.lessonplanner.data.local.CreditRedemptionEntity
import com.lp.lessonplanner.data.local.UserEntity
import com.lp.lessonplanner.data.remote.AuthRequest
import com.lp.lessonplanner.data.remote.RedeemRequest
import com.lp.lessonplanner.data.remote.UpdateResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Auth ──────────────────────────────────────────────────────────────────────

fun LessonPlanViewModel.loginOrRegister(phone: String, pin: String) {
    if (phone.length != 10 || !phone.all { it.isDigit() } || pin.length != 4) {
        _uiState.update { it.copy(errorMessage = "Invalid Phone (10 digits) or PIN (4 digits)") }
        return
    }
    viewModelScope.launch {
        try {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val token = VAULT_TOKEN

            var response = repository.login(token, AuthRequest(phone, pin))
            var wasRegistration = false

            if (!response.isSuccessful && response.code() == 404) {
                val regResponse = repository.register(token, AuthRequest(phone, pin, credits = 3))
                if (regResponse.code() == 409) {
                    response = repository.login(token, AuthRequest(phone, pin))
                } else {
                    response = regResponse
                    if (regResponse.isSuccessful) {
                        wasRegistration = true
                    }
                }
            }

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    // Ensure user is cached locally with consistent timestamp
                    val existingUser = repository.getLocalUser(phone)
                    val serverCreatedAt = body.createdAt
                    if (existingUser == null || existingUser.pin != pin || (serverCreatedAt != null && serverCreatedAt != existingUser.createdAt)) {
                        repository.insertLocalUser(UserEntity(
                            phone = phone,
                            pin = pin,
                            createdAt = serverCreatedAt ?: existingUser?.createdAt ?: System.currentTimeMillis()
                        ))
                    }
                    
                    _phoneNumber.value = phone
                    _pin.value = pin
                    _cloudUserId.value = phone
                    _isLoggedIn.value = true
                    _userCredits.value = body.credits
                    val receivedKey = body.apiKey ?: ""
                    _apiKey.value = receivedKey

                    repository.setSetting("phone_number", phone)
                    repository.setSetting("pin", pin)
                    repository.setSetting("user_credits", body.credits.toString())
                    repository.setSetting("api_key", receivedKey)

                    _uiState.update {
                        it.copy(errorMessage = if (wasRegistration) "Welcome! ${body.credits} free credits added." else "Logged In!")
                    }
                }
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Invalid PIN or Account Blocked"
                    403 -> "Account Access Denied"
                    409 -> "Phone number already registered"
                    400 -> "Auth failed. Please check your credentials."
                    else -> "Auth failed: ${response.code()}"
                }
                _uiState.update { it.copy(errorMessage = errorMsg) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Connection error: ${e.message}") }
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

fun LessonPlanViewModel.logout() {
    viewModelScope.launch {
        _phoneNumber.value = ""
        _pin.value = ""
        _cloudUserId.value = ""
        _isLoggedIn.value = false
        _userCredits.value = 0
        _apiKey.value = ""
        withContext(Dispatchers.IO) {
            repository.setSetting("phone_number", "")
            repository.setSetting("pin", "")
            repository.setSetting("user_credits", "0")
            repository.setSetting("api_key", "")
        }
    }
}

fun LessonPlanViewModel.factoryReset() {
    viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.clearAllData()
            repository.setSetting("api_key", "")
            repository.setSetting("model", "deepseek-chat")
            repository.setSetting("user_credits", "0")
            repository.setSetting("phone_number", "")
            repository.setSetting("pin", "")

            _apiKey.value = ""
            _model.value = "deepseek-chat"
            _userCredits.value = 0
            _phoneNumber.value = ""
            _pin.value = ""
            _isLoggedIn.value = false

            importCurriculumsFromAssets()
        }
    }
}

// ── Credits ───────────────────────────────────────────────────────────────────

fun LessonPlanViewModel.checkBalance() {
    val phone = _phoneNumber.value
    val pin = _pin.value
    if (phone.isEmpty() || pin.isEmpty()) return
    viewModelScope.launch {
        try {
            val response = repository.login(VAULT_TOKEN, AuthRequest(phone, pin))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    // Refresh local cache and handle potential timestamp updates from server
                    val existingUser = repository.getLocalUser(phone)
                    val serverCreatedAt = body.createdAt
                    if (existingUser == null || existingUser.pin != pin || (serverCreatedAt != null && serverCreatedAt != existingUser.createdAt)) {
                        repository.insertLocalUser(UserEntity(
                            phone = phone,
                            pin = pin,
                            createdAt = serverCreatedAt ?: existingUser?.createdAt ?: System.currentTimeMillis()
                        ))
                    }

                    _userCredits.value = body.credits
                    repository.setSetting("user_credits", body.credits.toString())
                    val receivedKey = body.apiKey ?: ""
                    if (receivedKey.isNotEmpty() && receivedKey != _apiKey.value) {
                        _apiKey.value = receivedKey
                        repository.setSetting("api_key", receivedKey)
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to refresh credits: ${e.message}") }
        }
    }
}

fun LessonPlanViewModel.addCredits(amount: Int) {
    viewModelScope.launch {
        val newCredits = _userCredits.value + amount
        _userCredits.value = newCredits
        repository.setSetting("user_credits", newCredits.toString())
        syncWithCloudflare()
    }
}

fun LessonPlanViewModel.redeemCode(code: String) {
    val trimmedCode = code.trim().uppercase()
    if (trimmedCode.isEmpty()) return
    viewModelScope.launch {
        try {
            _uiState.update { it.copy(isLoading = true) }
            val phone = _phoneNumber.value
            val response = repository.redeemCode(VAULT_TOKEN, RedeemRequest(trimmedCode, phone.ifEmpty { null }))
            if (response.isSuccessful) {
                val amount = response.body()?.amount ?: 0
                if (amount > 0) {
                    addCredits(amount)
                    repository.insertRedemption(CreditRedemptionEntity(code = trimmedCode, amount = amount))
                    _uiState.update { it.copy(errorMessage = "Success! $amount credits added.") }
                } else {
                    _uiState.update { it.copy(errorMessage = "Code verified, but amount is 0.") }
                }
            } else {
                val errorMsg = if (response.code() == 404) "Invalid or expired code." else "Server Error: ${response.code()}"
                _uiState.update { it.copy(errorMessage = errorMsg) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Connection error: ${e.localizedMessage}") }
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

fun LessonPlanViewModel.fetchUserCredits() {
    checkBalance()
    fetchCreditPlans(isSilent = false)
}

fun LessonPlanViewModel.loadRedemptionHistory(page: Int) {
    viewModelScope.launch {
        val limit = 5
        val offset = page * limit
        _currentRedemptionPage.value = page
        _redemptionHistory.value = repository.getRedemptionsPaginated(limit, offset)
        _totalRedemptions.value = repository.getRedemptionCount()
    }
}

// ── Credit plans & manual payment ─────────────────────────────────────────────

fun LessonPlanViewModel.fetchCreditPlans(isSilent: Boolean = false) {
    viewModelScope.launch {
        if (!isSilent) _isFetchingPlans.value = true
        try {
            val response = repository.getCreditPlans(VAULT_TOKEN)
            if (response.isSuccessful) {
                response.body()?.let { plans ->
                    _creditPlans.value = plans.map { CreditPackage(it.name ?: "Plan", it.count, it.price, it.priceString) }
                    if (!isSilent) _uiState.update { it.copy(errorMessage = "Credit plans refreshed successfully.") }
                }
            } else if (!isSilent) {
                _uiState.update { it.copy(errorMessage = "Failed to fetch plans: ${response.code()}") }
            }
        } catch (_: Exception) {
            if (!isSilent) _uiState.update { it.copy(errorMessage = "Connection error: Could not refresh plans.") }
        } finally {
            if (!isSilent) _isFetchingPlans.value = false
        }
    }
}

fun LessonPlanViewModel.initiateManualPayment(pkg: CreditPackage) {
    _showManualPaymentDialog.value = pkg
}

fun LessonPlanViewModel.showManualPayment(pkg: CreditPackage) = initiateManualPayment(pkg)

fun LessonPlanViewModel.dismissManualPayment() {
    _showManualPaymentDialog.value = null
}

fun LessonPlanViewModel.openWhatsApp(pkg: CreditPackage) {
    val message = "Hello, I would like to purchase the ${pkg.credits} Lesson Plan credits package for ${pkg.priceString}."
    openWhatsAppDirect(message)
}

fun LessonPlanViewModel.openWhatsAppDirect(message: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "https://wa.me/233553484762?text=${Uri.encode(message)}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    } catch (_: Exception) {
        _uiState.update { it.copy(errorMessage = "Could not open WhatsApp. Please contact 0553484762 manually.") }
    }
}

fun LessonPlanViewModel.openDialer() {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = "tel:0553484762".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    } catch (_: Exception) {
        _uiState.update { it.copy(errorMessage = "Could not open dialer.") }
    }
}

// ── Updates ───────────────────────────────────────────────────────────────────

fun LessonPlanViewModel.checkForUpdates(isSilent: Boolean = false) {
    viewModelScope.launch {
        try {
            val response = repository.checkForUpdate(VAULT_TOKEN)
            if (response.isSuccessful) {
                val update = response.body()
                if (update != null && update.versionCode > com.lp.lessonplanner.BuildConfig.VERSION_CODE) {
                    _updateAvailable.value = update
                } else if (!isSilent) {
                    _uiState.update { it.copy(errorMessage = "You are using the latest version.") }
                }
            } else if (!isSilent) {
                _uiState.update { it.copy(errorMessage = "Update check failed: ${response.code()}") }
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateCheck", "Failed to check for updates", e)
            if (!isSilent) {
                _uiState.update { it.copy(errorMessage = "Could not reach update server.") }
            }
        }
    }
}

fun LessonPlanViewModel.downloadUpdate(update: UpdateResponse) {
    updateManager.downloadAndInstall(update)
    _updateAvailable.value = null
}

fun LessonPlanViewModel.dismissUpdate() {
    _updateAvailable.value = null
}

fun LessonPlanViewModel.updateAiModel(model: String) {
    viewModelScope.launch {
        repository.setSetting("model", model)
        _model.value = model
    }
}

// ── Cloudflare sync (internal) ────────────────────────────────────────────────

internal fun LessonPlanViewModel.syncWithCloudflare() {
    val phone = _phoneNumber.value
    val pin = _pin.value
    if (phone.isEmpty() || pin.isEmpty()) return
    viewModelScope.launch {
        try {
            repository.syncUserData(
                VAULT_TOKEN,
                AuthRequest(phone = phone, pin = pin, credits = _userCredits.value, apiKey = _apiKey.value)
            )
        } catch (_: Exception) {
            android.util.Log.e("CloudSync", "Failed to sync data")
        }
    }
}

internal const val VAULT_TOKEN = "withGod2054*"
