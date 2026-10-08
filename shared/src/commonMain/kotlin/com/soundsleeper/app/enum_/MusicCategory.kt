package com.soundsleeper.app.enum_

import com.soundsleeper.app.ComponentResource
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.ic_bookmark_outlined
import com.soundsleeper.app.resources.music_category_ambient
import com.soundsleeper.app.resources.music_category_nature
import com.soundsleeper.app.resources.music_category_wave

enum class MusicCategory(override val resId: Any) : ComponentResource {
    FAVORITE(Res.drawable.ic_bookmark_outlined),
    NATURE(Res.string.music_category_nature),
    WAVE(Res.string.music_category_wave),
    AMBIENT(Res.string.music_category_ambient)
}
