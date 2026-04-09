package com.github.legiontube.ui.models

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.github.legiontube.api.obj.Segment
import com.github.legiontube.helpers.PlayerHelper

@UnstableApi
class PlayerViewModel : ViewModel() {

    var segments = MutableLiveData<List<Segment>>()
    // this is only used to restore the subtitle after leaving PiP, the actual caption state
    // should always be read from the player's selected tracks!
    var currentCaptionId: String? = null
    var sponsorBlockConfig = PlayerHelper.getSponsorBlockCategories()

    /**
     * Whether an orientation change is in progress, so that the current player should be continued to use
     *
     * Set to true if the activity will be recreated due to an orientation change
     */
    var isOrientationChangeInProgress = false
}