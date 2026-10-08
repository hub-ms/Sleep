package com.soundsleeper.app.data.sleep

import com.soundsleeper.app.domain.model.SleepAnalysis
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * 측정 중 실시간으로 쌓이는 수면 단계 예측([SleepAnalysis])을 세션이 끝날 때까지 메모리에 보관하는
 * 버퍼. [SleepSessionRepositoryImpl.analyzeSleepData]가 매 윈도우마다 [add]하고,
 * 측정이 끝나면 [analyzeSleepSession]이 [getAll]로 전체를 가져가 최종 리포트를 만든다.
 */
class PredictionHistoryManager {
    private val mutex = Mutex()
    private val history = mutableListOf<SleepAnalysis>()

    /** 실시간 분석 결과 하나를 히스토리에 추가한다(동시 접근 안전을 위해 mutex로 보호). */
    suspend fun add(analysis: SleepAnalysis) {
        Napier.d("prediction added: size=${history.size}, manager=${Clock.System.hashCode()}")
        mutex.withLock {
            history.add(analysis)
        }
    }

    /** 세션이 끝난 뒤(분석 완료) 히스토리를 비워, 다음 세션에 이전 데이터가 섞이지 않게 한다. */
    suspend fun clear() {
        mutex.withLock {
            history.clear()
        }
    }

    /** 지금까지 쌓인 모든 예측을 타임스탬프 순으로 정렬해 반환한다(최종 리포트 생성용). */
    suspend fun getAll(): List<SleepAnalysis> =
        mutex.withLock {
            history.sortedBy { it.timestamp }
        }
}