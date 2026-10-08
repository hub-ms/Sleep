package com.soundsleeper.app.domain.repository

import com.soundsleeper.app.domain.model.LegalDocument
import com.soundsleeper.app.enum_.LegalType

interface LegalRepository {
    suspend fun load(type: LegalType): LegalDocument
}