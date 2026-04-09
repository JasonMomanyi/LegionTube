package com.github.legiontube.api

import kotlinx.serialization.json.Json

object JsonHelper {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}
