package com.example.smartblindstick

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

object TranslationManager {

    private var translator: Translator? = null
    private var currentTargetCode: String? = null

    fun getLanguageCode(languageName: String): String {
        return when (languageName.lowercase()) {
            "hindi", "hi" -> TranslateLanguage.HINDI
            "marathi", "mr" -> TranslateLanguage.MARATHI
            "urdu", "ur" -> TranslateLanguage.URDU
            else -> TranslateLanguage.ENGLISH
        }
    }

    fun prepareTranslator(targetLangCode: String, onReady: () -> Unit, onError: (Exception) -> Unit) {
        if (currentTargetCode == targetLangCode && translator != null) {
            onReady()
            return
        }

        translator?.close()
        translator = null

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLangCode)
            .build()

        val newTranslator = Translation.getClient(options)

        // FIXED: Removed requireWifi() to allow language pack downloads on mobile data
        val conditions = DownloadConditions.Builder().build()

        newTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator = newTranslator
                currentTargetCode = targetLangCode
                onReady()
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun translateText(text: String, onResult: (String) -> Unit) {
        val activeTranslator = translator
        if (currentTargetCode == TranslateLanguage.ENGLISH || activeTranslator == null) {
            onResult(text)
            return
        }

        activeTranslator.translate(text)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult(text) }
    }
}