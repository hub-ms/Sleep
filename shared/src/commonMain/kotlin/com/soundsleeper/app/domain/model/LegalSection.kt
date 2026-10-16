package com.soundsleeper.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LegalSection(
    val number: Int,
    val heading: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
    val tableRows: List<LegalTableRow> = emptyList(),
    val callout: String? = null,
)