package com.soundsleeper.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LegalDocument(
    val type: String,
    val version: String,
    val effectiveDate: String,
    val updatedDate: String,
    val footerNote: String,
    val sections: List<LegalSection>,
)
