package com.lp.lessonplanner.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepSeekApi {
    @POST("chat/completions")
    suspend fun generateChat(
        @Header("Authorization") apiKey: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @GET("user/balance")
    suspend fun getBalance(
        @Header("Authorization") apiKey: String
    ): Response<BalanceResponse>
}

data class BalanceResponse(
    val is_available: Boolean,
    val balance_infos: List<BalanceInfo>
)

data class BalanceInfo(
    val currency: String,
    val total_balance: String,
    val granted_balance: String,
    val topped_up_balance: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 4000,
    val response_format: ResponseFormat? = null,
    val temperature: Double = 0.7
)

data class ResponseFormat(val type: String)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<ChatChoice>
)

data class ChatChoice(
    val message: ChatMessage
)
