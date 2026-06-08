package io.github.jasonmomanyi.legiontube.innertube.models.response

import io.github.jasonmomanyi.legiontube.innertube.models.Continuation
import io.github.jasonmomanyi.legiontube.innertube.models.MusicResponsiveListItemRenderer
import io.github.jasonmomanyi.legiontube.innertube.models.Tabs
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val contents: Contents?,
    val continuationContents: ContinuationContents?,
) {
    @Serializable
    data class Contents(
        val tabbedSearchResultsRenderer: Tabs?,
    )

    @Serializable
    data class ContinuationContents(
        val musicShelfContinuation: MusicShelfContinuation? = null,
    ) {
        @Serializable
        data class MusicShelfContinuation(
            val contents: List<Content>,
            val continuations: List<Continuation>?,
        ) {
            @Serializable
            data class Content(
                val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer,
            )
        }
    }
}
