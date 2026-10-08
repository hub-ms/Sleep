package com.somniotune.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LegalTableRow(val label: String, val value: String)