package com.github.legiontube.api.obj

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val error: String? = null,
    val message: String? = null
)
