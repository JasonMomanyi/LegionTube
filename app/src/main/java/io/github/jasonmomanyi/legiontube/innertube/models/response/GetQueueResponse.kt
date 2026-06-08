package io.github.jasonmomanyi.legiontube.innertube.models.response

import io.github.jasonmomanyi.legiontube.innertube.models.PlaylistPanelRenderer
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueResponse(
    val queueDatas: List<QueueData>,
) {
    @Serializable
    data class QueueData(
        val content: PlaylistPanelRenderer.Content,
    )
}
