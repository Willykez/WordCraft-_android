package com.willkez.wordcraft.ai

enum class ProviderKind { Gemini, OpenAi, Anthropic }

/** One entry in the provider list of AI settings. Model ids change often: presets are suggestions, the field is free text. */
data class ProviderInfo(
    val id: String,
    val label: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val defaultModel: String,
    val presets: List<Pair<String, String>>,
    val keyHelp: String,
    val note: String = "",
    val customUrl: Boolean = false,
    val jsonMode: Boolean = true,
    val keyOptional: Boolean = false,
)

/** What is saved for the provider in use. */
data class AiConfig(
    val provider: String = "gemini",
    val key: String = "",
    val model: String = AiProviders.byId("gemini").defaultModel,
    val baseUrl: String = "",
    /** Gemini: on a rate limit, carry on with a second model (each model has its own free quota). */
    val autoFallback: Boolean = true,
    /** Send the document's text with a request (better answers), or only the selection and the instruction. */
    val shareDocument: Boolean = true,
) {
    val info: ProviderInfo get() = AiProviders.byId(provider)
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { info.baseUrl }
    val ready: Boolean get() = (key.isNotBlank() || info.keyOptional) && model.isNotBlank() && (info.kind != ProviderKind.OpenAi || effectiveBaseUrl.isNotBlank())
}

object AiProviders {
    val all = listOf(
        ProviderInfo(
            "gemini", "Google Gemini", ProviderKind.Gemini, "", "gemini-4-hydrogen",
            listOf("Fast" to "gemini-4-hydrogen", "Smart" to "gemini-3.8-flash", "Lite" to "gemini-3.5-flash-lite", "Lightest" to "gemini-3.1-flash-lite"),
            "Free key: aistudio.google.com/app/apikey",
            "On a free key Google may use what you send (your instruction and the document text) to improve its products. Avoid sensitive documents, or turn on billing for the key.",
        ),
        ProviderInfo(
            "anthropic", "Claude", ProviderKind.Anthropic, "https://api.anthropic.com", "claude-sonnet-5-5",
            listOf("Balanced" to "claude-sonnet-5-5", "Fast" to "claude-haiku-5-5", "Best" to "claude-opus-5-5"),
            "Key: console.anthropic.com",
        ),
        ProviderInfo(
            "openai", "OpenAI", ProviderKind.OpenAi, "https://api.openai.com/v1", "gpt-6.2-sol",
            listOf("Sol" to "gpt-6.2-sol", "Astra" to "gpt-6.2-astra"),
            "Key: platform.openai.com/api-keys",
        ),
        ProviderInfo(
            "openrouter", "OpenRouter", ProviderKind.OpenAi, "https://openrouter.ai/api/v1", "openrouter/auto",
            listOf("Auto" to "openrouter/auto"),
            "One key for many models: openrouter.ai/keys. Type any model id from their list, such as vendor/model-name.",
        ),
        ProviderInfo(
            "groq", "Groq", ProviderKind.OpenAi, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile",
            listOf("Llama 3.3 70B" to "llama-3.3-70b-versatile"),
            "Key: console.groq.com/keys",
        ),
        ProviderInfo(
            "custom", "Your own server", ProviderKind.OpenAi, "", "",
            emptyList(),
            "Any OpenAI-compatible address: Ollama, LM Studio, DeepSeek, Mistral, Together and others.",
            "Examples: http://192.168.1.20:11434/v1 for Ollama on your computer (same Wi-Fi), https://api.deepseek.com/v1. A key is optional for local servers.",
            customUrl = true, jsonMode = false, keyOptional = true,
        ),
    )

    fun byId(id: String): ProviderInfo = all.firstOrNull { it.id == id } ?: all.first()

    /** The streaming client for the saved settings. */
    fun create(c: AiConfig): AiProvider = when (c.info.kind) {
        ProviderKind.Gemini -> GeminiClient(c.key, c.model, if (c.autoFallback) fallbackFor(c.model) else null)
        ProviderKind.Anthropic -> AnthropicClient(c.key, c.model, c.effectiveBaseUrl)
        ProviderKind.OpenAi -> OpenAiClient(c.effectiveBaseUrl, c.key, c.model, official = c.provider == "openai", jsonMode = c.info.jsonMode)
    }

    /** The backup for a free-tier rate limit: a different model, so a different quota. */
    fun fallbackFor(model: String): String? = when (model) {
        "gemini-3.8-flash" -> "gemini-3.5-flash-lite"
        "gemini-3.5-flash-lite" -> "gemini-3.1-flash-lite"
        "gemini-3.1-flash-lite" -> null
        else -> "gemini-3.8-flash"
    }

    /** Models Google is retiring map onto their replacements, so an old saved setting keeps working. */
    fun migrate(provider: String, model: String): String = if (provider != "gemini") model else when {
        model.startsWith("gemini-2.5-pro") -> "gemini-3.8-flash"
        model.startsWith("gemini-2.5-flash") -> "gemini-4-hydrogen"
        else -> model
    }
}
