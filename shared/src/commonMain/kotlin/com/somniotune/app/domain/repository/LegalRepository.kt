package com.somniotune.app.domain.repository

import com.somniotune.app.domain.model.LegalDocument
import com.somniotune.app.enum_.LegalType

interface LegalRepository {
    suspend fun load(type: LegalType): LegalDocument
}