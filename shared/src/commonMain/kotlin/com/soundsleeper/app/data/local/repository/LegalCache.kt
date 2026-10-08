package com.soundsleeper.app.data.local.repository
import com.russhwolf.settings.Settings
import com.soundsleeper.app.domain.model.LegalDocument
import com.soundsleeper.app.enum_.LegalType
import kotlinx.serialization.json.Json

class LegalCache(
    private val settings: Settings,
    private val json: Json,
) {
    fun save(type: LegalType, document: LegalDocument) {
        settings.putString(key(type), json.encodeToString(LegalDocument.serializer(), document))
    }

    fun load(type: LegalType): LegalDocument? {
        val raw = settings.getStringOrNull(key(type)) ?: return null
        return try {
            json.decodeFromString(LegalDocument.serializer(), raw)
        } catch (e: Exception) {
            // 스키마가 바뀌었거나 저장이 깨진 경우. 계속 실패하지 않게 지우고 폴백으로 넘긴다.
            settings.remove(key(type))
            null
        }
    }

    private fun key(type: LegalType) = "legal_cache_${type.path}"
}