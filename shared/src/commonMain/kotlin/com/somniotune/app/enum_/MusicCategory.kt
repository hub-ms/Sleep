package com.somniotune.app.enum_

import com.somniotune.app.ComponentResource
import com.somniotune.app.resources.Res
import com.somniotune.app.resources.ic_bookmark_outlined
import com.somniotune.app.resources.music_category_ambient
import com.somniotune.app.resources.music_category_nature
import com.somniotune.app.resources.music_category_wave

enum class MusicCategory(override val resId: Any) : ComponentResource {
    FAVORITE(Res.drawable.ic_bookmark_outlined),
    NATURE(Res.string.music_category_nature),
    WAVE(Res.string.music_category_wave),
    AMBIENT(Res.string.music_category_ambient)
}
