package com.sleepytime.shared.util

object NicknameGenerator {

    private val adjectives = listOf("Sleepy", "Dreamy", "Calm", "Silent", "Night")
    private val nouns = listOf("Moon", "Star", "Cloud", "Owl", "Dream")

    fun generate(): String {
        return "${adjectives.random()}${nouns.random()}${(100..999).random()}"
    }
}