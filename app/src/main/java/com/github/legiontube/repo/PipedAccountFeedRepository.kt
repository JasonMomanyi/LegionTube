package com.github.legiontube.repo

import com.github.legiontube.api.RetrofitInstance
import com.github.legiontube.api.obj.StreamItem
import com.github.legiontube.helpers.PreferenceHelper

class PipedAccountFeedRepository : FeedRepository {
    override suspend fun getFeed(
        forceRefresh: Boolean,
        onProgressUpdate: (FeedProgress) -> Unit
    ): List<StreamItem> {
        val token = PreferenceHelper.getToken()

        return RetrofitInstance.authApi.getFeed(token)
    }
}