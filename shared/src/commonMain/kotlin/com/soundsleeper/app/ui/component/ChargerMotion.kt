package com.soundsleeper.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.soundsleeper.app.resources.Res
import io.github.aakira.napier.Napier
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * 숙면 안내 화면의 충전기 모션.
 *
 * `composeResources/files/charger_motion.json` 이 있으면 그 Lottie 를 재생하고, **없으면
 * 기존 Canvas 일러스트로 떨어진다.**
 *
 * 폴백이 반드시 있어야 하는 이유는 이 레포가 이미 한 번 당한 적이 있기 때문이다. 예전
 * 스플래시 화면은 `files/splash_character.json` 을 Lottie 로 띄우려 했는데 그 파일이 실제로는
 * 없었다. 리소스 로드가 늘 실패해 컴포지션이 영원히 null 이었고, 폴백으로 둔 Canvas 는 크기
 * 지정이 없어 0×0 으로 아무것도 그리지 않았다 - 화면에는 아무 일도 일어나지 않았고 에러도
 * 없었다. 그래서 여기서는 (1) 실패를 로그로 남기고 (2) 폴백이 실제로 크기를 갖는 컴포저블이다.
 *
 * @param fallback Lottie 를 쓸 수 없을 때 그릴 내용. 호출부가 상황에 맞는 그림을 넘긴다.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun ChargerMotion(
    modifier: Modifier = Modifier,
    fallback: @Composable (Modifier) -> Unit,
) {
    var json by remember { mutableStateOf<String?>(null) }
    var isResolved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        json = runCatching { Res.readBytes(CHARGER_MOTION_PATH).decodeToString() }
            .onFailure { Napier.d("$CHARGER_MOTION_PATH 이 없어 Canvas 일러스트로 대체한다: ${it.message}") }
            .getOrNull()
        isResolved = true
    }

    // 파일 존재 여부가 정해지기 전에는 폴백을 그린다. 빈 화면을 띄웠다가 바꾸면 깜빡인다.
    val payload = json
    if (!isResolved || payload == null) {
        fallback(modifier)
        return
    }

    val composition by rememberLottieComposition { LottieCompositionSpec.JsonString(payload) }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Compottie.IterateForever,
    )

    // 컴포지션 파싱 자체가 실패할 수도 있다(손상된 json). 그때도 화면이 비지 않게 한다.
    val resolved = composition
    if (resolved == null) {
        fallback(modifier)
        return
    }

    Image(
        painter = rememberLottiePainter(composition = resolved, progress = { progress }),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

/** 디자이너가 넣어 줄 Lottie 파일 자리. 없으면 폴백이 그려진다. */
private const val CHARGER_MOTION_PATH = "files/charger_motion.json"
