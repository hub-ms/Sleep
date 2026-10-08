package com.somniotune.app.data.local.repository

import com.somniotune.app.domain.model.LegalDocument
import com.somniotune.app.enum_.LegalType
import com.somniotune.app.resources.Res
import kotlinx.serialization.json.Json

class BundledLegalSource(
    private val json: Json,
) {
    suspend fun load(type: LegalType): LegalDocument {
        val bytes = Res.readBytes("files/${type.path}.json")
        return json.decodeFromString(LegalDocument.serializer(), bytes.decodeToString())
    }
}