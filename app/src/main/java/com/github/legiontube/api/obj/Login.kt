package com.github.legiontube.api.obj

import kotlinx.serialization.Serializable

@Serializable
data class Login(
    val username: String,
    val password: String
)
