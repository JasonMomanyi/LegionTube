package com.github.legiontube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.github.legiontube.api.obj.Subscription
import com.github.legiontube.databinding.SubscriptionGroupChannelRowBinding
import com.github.legiontube.db.obj.SubscriptionGroup
import com.github.legiontube.extensions.toID
import com.github.legiontube.helpers.ImageHelper
import com.github.legiontube.helpers.NavigationHelper
import com.github.legiontube.ui.adapters.callbacks.DiffUtilItemCallback
import com.github.legiontube.ui.viewholders.SubscriptionGroupChannelRowViewHolder

class SubscriptionGroupChannelsAdapter(
    private val group: SubscriptionGroup,
    private val onGroupChanged: (SubscriptionGroup) -> Unit
) : ListAdapter<Subscription, SubscriptionGroupChannelRowViewHolder>(DiffUtilItemCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SubscriptionGroupChannelRowViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = SubscriptionGroupChannelRowBinding.inflate(layoutInflater, parent, false)
        return SubscriptionGroupChannelRowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubscriptionGroupChannelRowViewHolder, position: Int) {
        val channel = getItem(holder.bindingAdapterPosition)
        holder.binding.apply {
            root.setOnClickListener {
                NavigationHelper.navigateChannel(root.context, channel.url)
            }
            subscriptionChannelName.text = channel.name
            ImageHelper.loadImage(channel.avatar, subscriptionChannelImage, true)

            val channelId = channel.url.toID()
            channelIncluded.setOnCheckedChangeListener(null)
            channelIncluded.isChecked = group.channels.contains(channelId)
            channelIncluded.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) group.channels += channelId else group.channels -= channelId
                onGroupChanged(group)
            }
        }
    }
}
