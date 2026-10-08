package com.soundsleeper.app.platform

import com.soundsleeper.app.ui.tracking.TrackingContract
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 측정의 실제 센서/서비스/분석 수행을 담당하는 플랫폼별 구현체의 공통 인터페이스.
 * TrackingViewModel은 이 인터페이스만 보고 측정을 제어하며, 실제 구현(Android에서는
 * AndroidTrackingManager)이 포그라운드 서비스 시작, 센서 등록, 분석 호출, 저장까지 전부 처리한다.
 */
interface TrackingManager {
    /** 측정 상태(진행 중 여부, 경과 시간, 현재 수면 단계, 완료 여부 등)를 그대로 노출하는 흐름. */
    val trackingState: StateFlow<TrackingContract.State>
    /** 알람이 울렸을 때 해당 세션 ID를 한 번 흘려보내는 이벤트(기상 화면 진입 트리거). */
    val wakeAlarmEvent: SharedFlow<String>
    /** 서비스가 생성될 때 알림 갱신/포그라운드 종료 요청 콜백을 등록한다. */
    fun attachCallbacks(onNotificationUpdate: (String) -> Unit, onRequestStopForeground: () -> Unit)
    /** 측정을 시작한다 — 센서 등록, 포그라운드 서비스 기동, 세션 생성까지 트리거. */
    fun start(sessionId: String, durationMillis: Long, musicName: String?)
    /** 측정을 정상 종료하고 수집된 데이터를 분석·저장한다. */
    fun finish(sessionId: String, musicName: String?)
    /** 측정을 취소하고(저장 없이) 세션을 폐기한다. */
    fun discard(sessionId: String, musicName: String?)
    /** 측정 중 사용자가 알람(기상) 시각을 바꿨을 때 반영한다. */
    fun updateEndTime(hour: Int, minute: Int)
}