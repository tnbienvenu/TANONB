package com.cardiosuivi.app.ai

import com.cardiosuivi.app.data.AiProvider
import com.cardiosuivi.app.data.SettingsRepository
import com.cardiosuivi.app.security.SecretStore
import com.cardiosuivi.domain.ai.AiContext
import com.cardiosuivi.domain.ai.AiPromptBuilder
import com.cardiosuivi.domain.ai.AiSafetyGuard

data class AiAnswer(val text: String, val source: String, val sentToProvider: Boolean)

/**
 * Orchestration des appels à l'IA :
 * 1. garde-fou local (demandes de modification de traitement → refus local, rien n'est envoyé) ;
 * 2. vérification du consentement explicite à la transmission ;
 * 3. lecture de la clé d'API chiffrée ;
 * 4. envoi du contexte statistique pseudonymisé et de la question.
 */
class AiService(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val anthropic: AiClient = AnthropicClient(),
    private val openAi: AiClient = OpenAiClient(),
) {
    fun keyName(p: AiProvider) = "api_key_${p.name.lowercase()}"

    fun hasKey(p: AiProvider) = p != AiProvider.NONE && secrets.has(keyName(p))
    /** Enregistre la clé (chiffrée). Retourne false si la clé contient des caractères invalides. */
    fun saveKey(p: AiProvider, key: String): Boolean {
        val k = key.trim()
        if (!isValidApiKey(k)) return false
        secrets.put(keyName(p), k)
        return true
    }
    fun deleteKey(p: AiProvider) = secrets.remove(keyName(p))

    suspend fun ask(question: String, context: AiContext): AiAnswer {
        if (AiSafetyGuard.isPrescriptionRequest(question)) {
            return AiAnswer(AiSafetyGuard.REFUSAL, "Garde-fou local (aucune donnée transmise)", sentToProvider = false)
        }
        val s = settings.current()
        val provider = s.aiProvider
        if (provider == AiProvider.NONE) throw AiException("Aucun fournisseur d'IA n'est configuré.")
        if (s.consentFor(provider) == 0L) throw AiException("Consentement requis avant toute transmission de données.")
        val key = secrets.get(keyName(provider)) ?: throw AiException("Aucune clé d'API enregistrée pour ${provider.label}.")
        val model = if (provider == AiProvider.ANTHROPIC) s.anthropicModel else s.openAiModel
        val client = if (provider == AiProvider.ANTHROPIC) anthropic else openAi
        val text = client.complete(AiPromptBuilder.systemPrompt(s.emergencyNumber), AiPromptBuilder.userMessage(context, question), key, model)
        return AiAnswer(text, "${provider.label} · $model", sentToProvider = true)
    }

    /** Test de connexion sans aucune donnée de santé. */
    suspend fun testConnection(provider: AiProvider, model: String): String {
        val key = secrets.get(keyName(provider)) ?: throw AiException("Aucune clé d'API enregistrée.")
        val client = if (provider == AiProvider.ANTHROPIC) anthropic else openAi
        return client.complete("Réponds uniquement par le mot OK.", "Test de connexion.", key, model)
    }
}
