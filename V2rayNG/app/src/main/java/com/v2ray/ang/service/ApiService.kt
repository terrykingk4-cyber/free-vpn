package com.v2ray.ang.service

import com.v2ray.ang.dto.HandshakeRequest
import com.v2ray.ang.dto.HandshakeResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("/api/handshake")
    suspend fun handshake(@Body request: HandshakeRequest): Response<HandshakeResponse>

    companion object {
        // آی‌پی سرور خود را اینجا بگذارید
        private const val BASE_URL = "https://live.n-cpanel.xyz"

        fun create(): ApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}