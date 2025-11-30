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
        private const val SECONDARY_BASE_URL = "https://live.n-cpanel.xyz" 

        fun create(): ApiService {
            // تنظیم کلاینت با تایم‌اوت 5 ثانیه و اینترسپتور هوشمند
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS) // تایم‌اوت اتصال
                .readTimeout(5, TimeUnit.SECONDS)    // تایم‌اوت خواندن
                .writeTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(FailoverInterceptor()) // افزودن منطق تغییر دامین
                .build()

            return Retrofit.Builder()
                .baseUrl(PRIMARY_BASE_URL) // شروع با دامین اصلی
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }

    // اینترسپتور برای مدیریت خطا و تغییر دامین
    class FailoverInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            try {
                // تلاش اول: ارسال ریکوئست به دامین اصلی
                return chain.proceed(request)
            } catch (e: IOException) {
                // اگر ارور شبکه یا تایم‌اوت (بعد از 5 ثانیه) رخ داد:
                
                // 1. استخراج اطلاعات دامین دوم
                val secondaryUri = URI(SECONDARY_BASE_URL)
                
                // 2. ساخت URL جدید با جایگزینی هاست و اسکیم (http/https)
                val newUrl = request.url.newBuilder()
                    .scheme(secondaryUri.scheme)
                    .host(secondaryUri.host)
                    .port(if (secondaryUri.port != -1) secondaryUri.port else request.url.port)
                    .build()

                // 3. ساخت ریکوئست جدید با URL جدید
                val newRequest = request.newBuilder()
                    .url(newUrl)
                    .build()

                // 4. تلاش دوم: ارسال ریکوئست به دامین دوم
                return chain.proceed(newRequest)
            }
        }
    }
}