package com.soundsleeper.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.review.InAppReviewLauncher
import com.soundsleeper.app.ui.auth.AuthContract
import com.soundsleeper.app.ui.auth.AuthViewModel
import com.zoyi.channel.plugin.android.ChannelIO
import com.soundsleeper.app.ui.navigation.EmailAuthScreen
import com.soundsleeper.app.ui.navigation.SplashScreen
import com.soundsleeper.app.ui.navigation.TrackingScreen
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.soundsleeper.app.enum_.AppLanguage
import com.soundsleeper.app.ui.setting.AppearanceViewModel
import com.soundsleeper.app.ui.theme.SleepAppTheme
import com.soundsleeper.app.ui.tracking.TrackingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference
import kotlin.time.ExperimentalTime

@UnstableApi
@ExperimentalTime
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@ExperimentalSettingsApi
@InternalVoyagerApi
class MainActivity : ComponentActivity() {
    companion object {
        var instance: WeakReference<Activity>? = null
    }
    private val trackingViewModel: TrackingViewModel by inject()
    private val authViewModel: AuthViewModel by inject()
    private val appearanceViewModel: AppearanceViewModel by inject()
    override fun onCreate(savedInstanceState: Bundle?) {
        SingletonImageLoader.setSafe { context ->
            createImageLoader(context)
        }
        setTheme(R.style.Theme_SoundSleeper)
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
        if (ChannelIO.hasStoredPushNotification(this)) {
            ChannelIO.openStoredPushNotification(this)
        }
        setContent {
            val uiState by trackingViewModel.state.collectAsStateWithLifecycle()
            val language by appearanceViewModel.language.collectAsStateWithLifecycle()
            // 언어 전환은 Activity 재생성을 일으키므로 Compose 프레임 중이 아니라
            // LaunchedEffect 에서 한 번만 호출한다. AppCompat 백포트가 API 33 미만에서도
            // per-app locale 을 동작하게 한다(minSdk 31).
            LaunchedEffect(language) {
                val localeList = when (language) {
                    AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                    AppLanguage.KO -> LocaleListCompat.forLanguageTags("ko")
                    AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
                }
                if (AppCompatDelegate.getApplicationLocales() != localeList) {
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
            }
            SleepAppTheme {

                val startScreen = remember {
                    if (uiState.isTracking) {
                        TrackingScreen(
                            durationMillis = uiState.durationMillis,
                            sessionId = uiState.sessionId ?: ""
                        )
                    } else {
                        // 온보딩이 아니라 스플래시로 시작한다. 로그인 상태가 정해지기 전에
                        // 온보딩을 먼저 띄우면 이미 로그인한 사용자에게 그 화면이 한 번
                        // 번쩍이고 지나간다.
                        SplashScreen
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
            LaunchedEffect(Unit) {
                authViewModel.effect.collect { effect ->
                    when (effect) {
                        AuthContract.Effect.LaunchInAppReview -> {
                            InAppReviewLauncher.launch(this@MainActivity)
                        }
                        else -> {}
                    }
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

            // 딥링크의 email/code 는 민감정보이므로 로그로 남기지 않는다.

            if (email.isNotEmpty() && code.isNotEmpty()) {
                navigator.push(
                    EmailAuthScreen(email, code)
                )
            }
        }
    }

    fun createImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
            }
            .build()
}