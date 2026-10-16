package com.soundsleeper.app.ui.common

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

abstract class AppScopedScreenModel : ScreenModel {
    protected val modelScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
