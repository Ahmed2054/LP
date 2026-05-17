package com.lp.lessonplanner.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface CloudflareVaultApi {
    @POST("api/vault/login")
    suspend fun login(
        @Header("X-Vault-Token") token: String,
        @Body request: AuthRequest
    ): Response<AuthResponse>

    @POST("api/vault/register")
    suspend fun register(
        @Header("X-Vault-Token") token: String,
        @Body request: AuthRequest
    ): Response<AuthResponse>

    @POST("api/vault/sync")
    suspend fun syncUserData(
        @Header("X-Vault-Token") token: String,
        @Body request: AuthRequest
    ): Response<Unit>

    @GET("api/vault/{userId}")
    suspend fun getKey(
        @Header("X-Vault-Token") token: String,
        @Path("userId") userId: String
    ): Response<VaultResponse>

    @POST("api/vault/redeem")
    suspend fun redeemCode(
        @Header("X-Vault-Token") token: String,
        @Body request: RedeemRequest
    ): Response<RedeemResponse>

    @GET("api/credit-plans")
    suspend fun getCreditPlans(
        @Header("X-Vault-Token") token: String
    ): Response<List<CreditPackageResponse>>

    @GET("api/update-check")
    suspend fun checkForUpdate(
        @Header("X-Vault-Token") token: String
    ): Response<UpdateResponse>
}

data class AuthRequest(
    val phone: String,
    val pin: String,
    val credits: Int? = null,
    val apiKey: String? = null
)

data class AuthResponse(
    val apiKey: String? = null,
    val credits: Int = 0,
    val error: String? = null,
    val createdAt: Long? = null
)

data class UpdateResponse(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean
)

data class CreditPackageResponse(
    val name: String? = null,
    val count: Int,
    val price: Double,
    val priceString: String
)

data class RedeemRequest(
    val code: String,
    val phone: String? = null
)

data class RedeemResponse(
    val amount: Int,
    val message: String? = null
)

data class VaultRequest(
    val userId: String,
    val apiKey: String
)

data class VaultResponse(
    val apiKey: String
)
