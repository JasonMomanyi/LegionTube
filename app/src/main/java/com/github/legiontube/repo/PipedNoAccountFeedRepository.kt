package com.github.legiontube.repo

import com.github.legiontube.api.RetrofitInstance
import com.github.legiontube.api.SubscriptionHelper
import com.github.legiontube.api.SubscriptionHelper.GET_SUBSCRIPTIONS_LIMIT
import com.github.legiontube.api.obj.StreamItem

class PipedNoAccountFeedRepository : FeedRepository {
    override suspend fun getFeed(
        forceRefresh: Boolean,
        onProgressUpdate: (FeedProgress) -> Unit
    ): List<StreamItem> {
        val channelIds = SubscriptionHelper.getSubscriptionChannelIds()

        return when {
            channelIds.size > GET_SUBSCRIPTIONS_LIMIT ->
                RetrofitInstance.authApi
                    .getUnauthenticatedFeed(channelIds)

            else -> RetrofitInstance.authApi.getUnauthenticatedFeed(
                channelIds.joinToString(",")
            )
        }
    }
}