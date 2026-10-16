package com.soundsleeper.app.dto_new.response

data class LegalSectionResponse(
    val number: Int,
    val heading: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
    val tableRows: List<LegalTableRowResponse> = emptyList(),
    val callout: String? = null,
)
