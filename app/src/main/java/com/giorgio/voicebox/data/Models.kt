package com.giorgio.voicebox.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String = "unknown",
    @SerialName("backend_type") val backendType: String? = null,
    @SerialName("gpu_type") val gpuType: String? = null,
    @SerialName("model_loaded") val modelLoaded: Boolean = false,
    @SerialName("model_size") val modelSize: String? = null,
)

@Serializable
data class VoiceProfile(
    val id: String,
    val name: String,
    val description: String? = null,
    val language: String = "en",
    @SerialName("voice_type") val voiceType: String = "cloned",
    @SerialName("default_engine") val defaultEngine: String? = null,
    @SerialName("preset_engine") val presetEngine: String? = null,
    @SerialName("generation_count") val generationCount: Int = 0,
    @SerialName("sample_count") val sampleCount: Int = 0,
) {
    /** Mirrors the backend's engine resolution so validation never trips. */
    val effectiveEngine: String?
        get() = defaultEngine ?: presetEngine
}

@Serializable
data class GenerateRequest(
    @SerialName("profile_id") val profileId: String,
    val text: String,
    val language: String = "en",
    val engine: String? = null,
)

@Serializable
data class Generation(
    val id: String,
    val status: String = "completed",
    val error: String? = null,
    val duration: Double? = null,
)
