package com.soundsleeper.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 테스트 인프라 자체가 동작하는지 확인하는 스모크 테스트.
 *
 * 실제 로직 테스트를 작성하기 전에 이 테스트가 통과해야 한다. 통과하지 않으면
 * 뒤이은 빨간 테스트가 "로직 버그" 때문인지 "배선 문제" 때문인지 구분할 수 없다.
 */
class InfraSmokeTest {

    @Test
    fun kotlinTestRuns() {
        assertEquals(2, 1 + 1)
    }

    @Test
    fun coroutinesTestRuns() = runTest {
        var touched = false
        touched = true
        assertTrue(touched, "runTest 본문이 실행되지 않았다")
    }
}
