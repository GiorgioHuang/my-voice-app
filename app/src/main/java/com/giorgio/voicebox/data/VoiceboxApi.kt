package com.giorgio.voicebox.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface VoiceboxApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("profiles")
    suspend fun profiles(): List<VoiceProfile>

    @POST("generate")
    suspend fun generate(@Body request: GenerateRequest): Generation

    @GET("history/{id}")
    suspend fun generation(@Path("id") id: String): Generation

    @GET("models/status")
    suspend fun modelStatus(): ModelStatusListResponse
}

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var cached: Pair<String, VoiceboxApi>? = null

    /**
     * Accepts "192.168.1.10:17493", "http://mac.tail1234.ts.net:17493/", etc.
     * Returns a canonical base URL ending in "/".
     */
    fun normalizeBaseUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) url += "/"
        return url
    }

    fun api(baseUrl: String): VoiceboxApi {
        val normalized = normalizeBaseUrl(baseUrl)
        cached?.let { (url, api) -> if (url == normalized) return api }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // /generate may block while the model loads or synthesizes.
            .readTimeout(10, TimeUnit.MINUTES)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(VoiceboxApi::class.java)

        cached = normalized to api
        return api
    }

    fun audioUrl(baseUrl: String, generationId: String): String =
        normalizeBaseUrl(baseUrl) + "audio/" + generationId
}
