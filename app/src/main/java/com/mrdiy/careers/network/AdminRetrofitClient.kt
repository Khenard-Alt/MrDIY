package com.mrdiy.careers.network

import com.mrdiy.careers.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * A SEPARATE Retrofit client from [RetrofitClient] — that one talks to the PHP backend
 * (mrd-i-y-careers.onrender.com); this one talks to mrdiy-admin's Next.js deployment, the
 * first-ever Android-to-mrdiy-admin bridge (§6.3, CAPSTONE_ADVANCED_AI_PLANNING.md). Only the
 * public, unauthenticated `/api/public/faq-chat` endpoint is reachable this way — mrdiy-admin's
 * other routes require an admin session this app never has.
 *
 * Deployed on Vercel from github.com/Khenard-Alt/mrdiy-admin.
 */
object AdminRetrofitClient {

    private const val BASE_URL = "https://mrdiy-admin-three.vercel.app/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val faqApiService: FaqApiService = retrofit.create(FaqApiService::class.java)
}
