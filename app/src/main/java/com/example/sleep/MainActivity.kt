package com.example.sleep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.sleep.presentation.navigation.AppNavGraph

class MainActivity : ComponentActivity() {
    private lateinit var sleepClassifier: SleepStageClassifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the classifier
        sleepClassifier = SleepStageClassifier(applicationContext)

        val dummyInputData = FloatArray(3000) // The model expects input of shape [1, 3000, 1]

        // Classify the sleep stage
        val sleepStage = sleepClassifier.classify(dummyInputData)

        enableEdgeToEdge()
        setContent {
            AppNavGraph(
                navController = rememberNavController(), viewModel = viewModel(), sleepStage = sleepStage
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
