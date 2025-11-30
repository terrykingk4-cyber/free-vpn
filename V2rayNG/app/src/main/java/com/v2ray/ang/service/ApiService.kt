package com.v2ray.ang.service

import com.v2ray.ang.dto.HandshakeRequest
import com.v2ray.ang.dto.HandshakeResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("/api/handshake")
    suspend fun handshake(@Body request: HandshakeRequest): retrofit2.Response<HandshakeResponse>

    companion object {
        // دامین اول (اصلی)
        private const val PRIMARY_BASE_URL = "https://live.n-cpanel.xyz"
        
        // دامین دوم (بک‌آپ) - آدرس دامین دوم خود را اینجا وارد کنید
        private const val SECONDARY_BASE_URL = "https://backup.example.com" 

        fun create(): ApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(FailoverInterceptor())
                .build()

            return Retrofit.Builder()
                .baseUrl(PRIMARY_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }

    class FailoverInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                try {
                    val secondaryUri = URI(SECONDARY_BASE_URL)
                    
                    // استفاده از url() به جای url برای جلوگیری از خطای دسترسی به فیلد
                    val currentUrl = request.url() 
                    val newUrlBuilder = currentUrl.newBuilder()
                        .scheme(secondaryUri.scheme)
                        .host(secondaryUri.host)
                    
                    // استفاده از port() به جای port
                    val newPort = if (secondaryUri.port != -1) secondaryUri.port else currentUrl.port()
                    newUrlBuilder.port(newPort)

                    val newRequest = request.newBuilder()
                        .url(newUrlBuilder.build())
                        .build()

                    return chain.proceed(newRequest)
                } catch (e2: Exception) {
                    // اگر دامین دوم هم فیل شد، ارور اصلی را برگردان
                    throw e
                }
            }
        }
    }
}