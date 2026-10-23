package com.jatta.study

import kotlinx.serialization.Serializable

/** The learner's choice of look; System follows the phone's light or dark setting. */
@Serializable
enum class ThemeChoice { System, Light, Dark }
