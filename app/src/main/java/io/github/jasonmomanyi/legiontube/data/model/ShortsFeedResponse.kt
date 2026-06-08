package io.github.jasonmomanyi.legiontube.data.model

data class ShortsFeedResponse(
    val videos: List<ShortItem>,
    val nextContinuationToken: String?
)
