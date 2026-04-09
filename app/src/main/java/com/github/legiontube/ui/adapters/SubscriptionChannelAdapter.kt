package com.github.legiontube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.ListAdapter
import com.github.legiontube.api.obj.Subscription
import com.github.legiontube.constants.IntentData
import com.github.legiontube.databinding.ChannelSubscriptionRowBinding
import com.github.legiontube.extensions.toID
import com.github.legiontube.helpers.ContextHelper
import com.github.legiontube.helpers.ImageHelper
import com.github.legiontube.helpers.NavigationHelper
import com.github.legiontube.ui.adapters.callbacks.DiffUtilItemCallback
import com.github.legiontube.ui.base.BaseActivity
import com.github.legiontube.ui.extensions.setupSubscriptionButton
import com.github.legiontube.ui.sheets.ChannelOptionsBottomSheet
import com.github.legiontube.ui.viewholders.SubscriptionChannelViewHolder

class SubscriptionChannelAdapter :
    ListAdapter<Subscription, SubscriptionChannelViewHolder>(DiffUtilItemCallback()) {

    // Track recently unsubscribed channels to preserve their unsubscribed state when
    // [onBindViewHolder] is re-called on these channels while scrolling the [RecyclerView]
    private val recentlyUnsubscribedList = mutableListOf<String>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SubscriptionChannelViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ChannelSubscriptionRowBinding.inflate(layoutInflater, parent, false)
        return SubscriptionChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubscriptionChannelViewHolder, position: Int) {
        val subscription = getItem(holder.bindingAdapterPosition)

        holder.binding.apply {
            subscriptionChannelName.text = subscription.name
            ImageHelper.loadImage(subscription.avatar, subscriptionChannelImage, true)

            root.setOnClickListener {
                NavigationHelper.navigateChannel(root.context, subscription.url)
            }
            root.setOnLongClickListener {
                val channelOptionsSheet = ChannelOptionsBottomSheet()
                channelOptionsSheet.arguments = bundleOf(
                    IntentData.channelId to subscription.url.toID(),
                    IntentData.channelName to subscription.name,
                    IntentData.isSubscribed to true
                )
                val activity = ContextHelper.unwrapActivity<BaseActivity>(root.context)
                channelOptionsSheet.show(activity.supportFragmentManager)
                true
            }

            val channelId = subscription.url.toID()
            val isRecentlyUnsubscribed = recentlyUnsubscribedList.any { it == channelId }
            subscriptionSubscribe.setupSubscriptionButton(
                channelId,
                subscription.name,
                subscription.avatar,
                subscription.verified,
                notificationBell,
                !isRecentlyUnsubscribed
            ) { isSubscribed ->
                when (isSubscribed) {
                    true -> if (isRecentlyUnsubscribed) recentlyUnsubscribedList.remove(channelId)
                    false -> if (!isRecentlyUnsubscribed) recentlyUnsubscribedList.add(channelId)
                }
            }
        }
    }
}
