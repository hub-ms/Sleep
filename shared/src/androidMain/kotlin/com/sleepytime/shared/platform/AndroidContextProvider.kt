package com.sleepytime.shared.platform

import android.content.Context
import java.lang.ref.WeakReference

object AndroidContextProvider {
    // Context를 직접 들고 있지 않고 WeakReference 객체로 감싸서 보관합니다.
    private var contextRef: WeakReference<Context>? = null

    var context: Context
        get() = contextRef?.get() ?: throw IllegalStateException("Context가 아직 초기화되지 않았습니다.")
        set(value) {
            contextRef = WeakReference(value.applicationContext)
        }
}