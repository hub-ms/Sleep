package com.example.sleep.presentation.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleep.SleepStageClassifier
import com.example.sleep.domain.SleepStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random


data class SleepReport(
    val timeline: List<SleepStage>,
    val totalSleepTime: String,
    val stageDurations: Map<SleepStage, String>,
    val sleepScore: Int
)

class ReportViewModel(application: Application) : AndroidViewModel(application) {
    var report by mutableStateOf<SleepReport?>(null)
        private set
    private val classifier: SleepStageClassifier by lazy {
        SleepStageClassifier(application.applicationContext)
    }

    init {
        generateDemoReport()
    }

    private fun generateDemoReport() {
        viewModelScope.launch(Dispatchers.Default) {
            // Simulate network/processing delay
            delay(1500)

            // Create a demo sleep timeline for ~8 hours (960 epochs of 30s)
            val demoTimeline = mutableListOf<SleepStage>()
            repeat(20) { demoTimeline.add(SleepStage.AWAKE) } // 10 mins to fall asleep
            repeat(15) { demoTimeline.add(SleepStage.LIGHT) }
            repeat(60) { demoTimeline.add(SleepStage.LIGHT) }
            repeat(40) { demoTimeline.add(SleepStage.LIGHT) }
            repeat(30) { demoTimeline.add(SleepStage.LIGHT) }
            repeat(25) { demoTimeline.add(SleepStage.REM) }
            // Cycle 2
            repeat(10) { demoTimeline.add(SleepStage.LIGHT) }
            repeat(70) { demoTimeline.add(SleepStage.LIGHT) }
            repeat(50) { demoTimeline.add(SleepStage.LIGHT) }
            repeat(40) { demoTimeline.add(SleepStage.LIGHT) }
            repeat(30) { demoTimeline.add(SleepStage.REM) }
            // ... add more cycles to fill ~8 hours

            // For demo, let's just repeat the current list to make it longer
            val finalTimeline = (0..3).flatMap { demoTimeline }.toMutableList()
            repeat(20) { finalTimeline.add(SleepStage.AWAKE) } // Woke up at the end


            // Calculate durations
            val stageCounts = finalTimeline.groupingBy { it }.eachCount()
            val stageDurations = mutableMapOf<SleepStage, String>()

            val totalSleepMinutes = (finalTimeline.size - (stageCounts[SleepStage.AWAKE] ?: 0)) / 2
            val totalSleepTime = "${totalSleepMinutes / 60}시간 ${totalSleepMinutes % 60}분"

            val stageMinutes = (stageCounts[SleepStage.REM] ?: 0) / 2
            stageDurations[SleepStage.REM] = "${stageMinutes / 60}시간 ${stageMinutes % 60}분"

            val lightSleepMinutes = ((stageCounts[SleepStage.LIGHT] ?: 0) + (stageCounts[SleepStage.LIGHT] ?: 0)) / 2
            // Use N1 for light sleep representation in the map
            stageDurations[SleepStage.LIGHT] = "${lightSleepMinutes / 60}시간 ${lightSleepMinutes % 60}분"


            val deepSleepMinutes = (stageCounts[SleepStage.DEEP] ?: 0) / 2
            // Use N3 for deep sleep representation in the map
            stageDurations[SleepStage.DEEP] = "${deepSleepMinutes / 60}시간 ${deepSleepMinutes % 60}분"

            val wakeMinutes = (stageCounts[SleepStage.AWAKE] ?: 0) / 2
            stageDurations[SleepStage.AWAKE] = "${wakeMinutes / 60}시간 ${wakeMinutes % 60}분"

            // Create a final map for UI, grouping N1/N2 and N3
            val finalStageDurations = mapOf(
                SleepStage.AWAKE to stageDurations.getOrDefault(SleepStage.AWAKE, "0시간 0분"),
                SleepStage.REM to stageDurations.getOrDefault(SleepStage.REM, "0시간 0분"),
                SleepStage.LIGHT to stageDurations.getOrDefault(SleepStage.LIGHT, "0시간 0분"), // Represents Light Sleep
                SleepStage.DEEP to stageDurations.getOrDefault(SleepStage.DEEP, "0시간 0분")  // Represents Deep Sleep
            )


            // Calculate sleep score
            val sleepScore = Random.nextInt(70, 95)

            report = SleepReport(
                timeline = finalTimeline,
                totalSleepTime = totalSleepTime,
                stageDurations = finalStageDurations,
                sleepScore = sleepScore
            )
        }
    }

    fun predictSleepStage(eegData: FloatArray) {
        val stageIndex = classifier.classify(eegData)
        when (stageIndex) {
            0 -> "Wake"
            1 -> "N1"
            2 -> "N2"
            3 -> "N3"
            4 -> "REM"
            else -> "Error"
        }
    }
}
