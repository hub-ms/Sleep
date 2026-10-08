package com.soundsleeper.app.dto_new.response

data class LegalDocumentResponse(
    val type: String,
    val version: String,
    val effectiveDate: String,
    val updatedDate: String,
    val footerNote: String,
    val sections: List<LegalSectionResponse>,
)