package com.soundsleeper.app.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 수면 음악 카드의 경과/잔여 시간 포맷.
 *
 * 분/초 두 칸만 쓰던 시절에는 세 시간짜리 밤이 `180:00` 으로 찍혔다. 밤새 트는 음악에 쓰는
 * 포맷이라 한 시간 초과가 예외가 아니라 기본값에 가깝다. 시 자리가 다시 사라지면 이 테스트가
 * 깨진다.
 */
class SleepMusicSecondsFormatTest {

    @Test
    fun underAnHourStaysMinuteSecond() {
        assertEquals("00:00", DateTimeUtil.formatSleepMusicSeconds(0))
        assertEquals("00:09", DateTimeUtil.formatSleepMusicSeconds(9))
        assertEquals("01:05", DateTimeUtil.formatSleepMusicSeconds(65))
        assertEquals("59:59", DateTimeUtil.formatSleepMusicSeconds(3599))
    }

    @Test
    fun anHourOrMoreGainsAnHourField() {
        assertEquals("1:00:00", DateTimeUtil.formatSleepMusicSeconds(3600))
        assertEquals("1:01:05", DateTimeUtil.formatSleepMusicSeconds(3665))
        // 예전에는 "180:00" 이었다.
        assertEquals("3:00:00", DateTimeUtil.formatSleepMusicSeconds(10_800))
        assertEquals("8:30:07", DateTimeUtil.formatSleepMusicSeconds(30_607))
    }

    /** 경과 시간은 뺄셈으로 만들어지므로 음수가 흘러들 수 있다. 0 으로 바닥을 받는다. */
    @Test
    fun negativeSecondsClampToZero() {
        assertEquals("00:00", DateTimeUtil.formatSleepMusicSeconds(-1))
        assertEquals("00:00", DateTimeUtil.formatSleepMusicSeconds(-10_000))
    }
}
