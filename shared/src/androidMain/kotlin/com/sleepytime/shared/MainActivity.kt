package com.sleepytime.shared

import android.R.attr.data
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.ui.navigation.EmailAuthScreen
import com.sleepytime.shared.ui.navigation.OnboardingScreen
import com.sleepytime.shared.ui.navigation.TrackingScreen
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.tracking.TrackingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.jvm.java
import kotlin.random.Random
import kotlin.time.ExperimentalTime

@UnstableApi
@ExperimentalTime
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@ExperimentalSettingsApi
class MainActivity : ComponentActivity() {
    companion object {
        var instance: WeakReference<Activity>? = null
    }
    private val trackingViewModel: TrackingViewModel by inject()
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_SleepyTime)
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
        lifecycleScope.launch {
            generateAllSimulationFiles()
        }
        setContent {
            val uiState by trackingViewModel.state.collectAsStateWithLifecycle()
            SleepAppTheme {

                val startScreen = remember {
                    if (uiState.isTracking) {
                        TrackingScreen(
                            duration = uiState.duration,
                            sessionId = uiState.sessionId ?: ""
                        )
                    } else {
                        OnboardingScreen
                    }
                }
                Navigator(
                    screen = startScreen,
                    disposeBehavior = NavigatorDisposeBehavior(
                        disposeNestedNavigators = false,
                        disposeSteps = false
                    )
                ) { navigator ->
                    val currentNavigator = remember { navigator }
                    LaunchedEffect(intent) {
                        intent?.data?.let { uri ->
                            handleDeepLink(uri, currentNavigator)
                        }
                    }
                    CurrentScreen()
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            Log.d("MainActivity", "onNewIntent: $uri")
        }
    }

    private fun handleDeepLink(uri: Uri, navigator: Navigator) {
        if (uri.scheme == "sleepapp" && uri.host == "auth") {
            val email = uri.getQueryParameter("email") ?: ""
            val code = uri.getQueryParameter("code") ?: ""

            Log.d("SleepNavHost", "?�� ?�싱 결과 - email: $email, code: $code")

            if (email.isNotEmpty() && code.isNotEmpty()) {
                navigator.push(
                    EmailAuthScreen(email, code)
                )
            }
        }
    }

    private suspend fun generateAllSimulationFiles() {
        Log.d("SIMULATION", "모든 파일 생성 시작")
        try {
            val subjects = mutableListOf<String>()
            for (i in 0..9) {
                subjects.add(String.format(Locale.US, "SC40%d1", i))
                subjects.add(String.format(Locale.US, "SC40%d2", i))
            }

            subjects.forEach { subjectId ->
                val result = createRandomWeatherFile(subjectId)
                if (result) {
                    Log.d("SIMULATION", "생성 성공: ${subjectId}_weather.json")
                }
            }
            Log.d("SIMULATION", "모든 파일 생성 완료 (Device Explorer 에서 확인하세요)")
        } catch (e: Exception) {
            Log.e("SIMULATION", "파일 생성 중 예외: ${e.message}", e)
        }
    }

    private suspend fun createRandomWeatherFile(subjectId: String): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val dir = File(filesDir, "weather_simulation")
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                // 2. ?�덤 ?�이???�성
                val randomTemp = Random.nextDouble(18.0, 28.0)
                val randomHumi = Random.nextDouble(30.0, 70.0)
                val seasons = listOf("Spring", "Summer", "Autumn", "Winter")

                // 3. JSON 구성
                val payload = JSONObject().apply {
                    put("subjectId", subjectId)
                    put("timestamp", System.currentTimeMillis())
                    put("indoorTemp", String.format(Locale.US, "%.2f", randomTemp))
                    put("indoorHumi", String.format(Locale.US, "%.2f", randomHumi))
                    put("season", seasons.random())
                    put("poorSleepTags", JSONArray(listOf("simulation_data")))
                }

                // 4. ?�일 ?�기
                val file = File(dir, "${subjectId}_weather.json")
                file.writeText(payload.toString(2))
                true
            } catch (e: Exception) {
                Log.e("SIMULATION", "?�� ?�일 ?�기 ?�패 ($subjectId): ${e.message}")
                false
            }
        }
}