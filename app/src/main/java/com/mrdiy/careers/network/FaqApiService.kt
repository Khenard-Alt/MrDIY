package com.mrdiy.careers.network

import retrofit2.http.Body
import retrofit2.http.POST

/** §6.3 Conversational FAQ assistant — talks to mrdiy-admin's public faq-chat endpoint, not the PHP backend. */
interface FaqApiService {

    @POST("api/public/faq-chat")
    suspend fun askQuestion(@Body request: FaqChatRequest): FaqChatResponse
}

data class FaqChatRequest(
    val jobId: String,
    val question: String
)

data class FaqChatResponse(
    val ok: Boolean,
    val answer: String? = null,
    val reason: String? = null,
    val error: String? = null
)
