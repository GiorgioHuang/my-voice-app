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
    @SerialName("model_size") val modelSize: String? = null,
)

@Serializable
data class ModelStatus(
    @SerialName("model_name") val modelName: String,
    @SerialName("display_name") val displayName: String,
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    @SerialName("size_mb") val sizeMb: Double? = null,
    val loaded: Boolean = false,
)

@Serializable
data class ModelStatusListResponse(
    val models: List<ModelStatus>,
)

/** A TTS model choice the user can generate with. */
data class TtsModel(
    val modelName: String,
    val displayName: String,
    val engine: String,
    val modelSize: String?,
    val downloaded: Boolean,
    val loaded: Boolean,
)

/**
 * Maps the backend's model registry names to (engine, model_size) pairs
 * used by POST /generate. Unknown and non-TTS models return null.
 */
fun ModelStatus.toTtsModel(): TtsModel? {
    val engineAndSize: Pair<String, String?> = when (modelName) {
        "qwen-tts-1.7B" -> "qwen" to "1.7B"
        "qwen-tts-0.6B" -> "qwen" to "0.6B"
        "qwen-custom-voice-1.7B" -> "qwen_custom_voice" to "1.7B"
        "qwen-custom-voice-0.6B" -> "qwen_custom_voice" to "0.6B"
        "luxtts" -> "luxtts" to null
        "chatterbox-tts" -> "chatterbox" to null
        "chatterbox-turbo" -> "chatterbox_turbo" to null
        "tada-1b" -> "tada" to "1B"
        "tada-3b-ml" -> "tada" to "3B"
        "kokoro" -> "kokoro" to null
        else -> return null
    }
    return TtsModel(
        modelName = modelName,
        displayName = displayName,
        engine = engineAndSize.first,
        modelSize = engineAndSize.second,
        downloaded = downloaded,
        loaded = loaded,
    )
}

/** Languages accepted by POST /generate. */
val SUPPORTED_LANGUAGES = listOf(
    "zh" to "中文",
    "en" to "English",
    "ja" to "日本語",
    "ko" to "한국어",
    "de" to "Deutsch",
    "fr" to "Français",
    "es" to "Español",
    "pt" to "Português",
    "it" to "Italiano",
    "ru" to "Русский",
)

@Serializable
data class Generation(
    val id: String,
    val status: String = "completed",
    val error: String? = null,
    val duration: Double? = null,
)
