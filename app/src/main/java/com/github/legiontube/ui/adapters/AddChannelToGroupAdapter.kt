package com.github.legiontube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.github.legiontube.databinding.AddChannelToGroupRowBinding
import com.github.legiontube.db.obj.SubscriptionGroup
import com.github.legiontube.ui.adapters.callbacks.DiffUtilItemCallback
import com.github.legiontube.ui.viewholders.AddChannelToGroupViewHolder

class AddChannelToGroupAdapter(
    private val channelId: String
) : ListAdapter<SubscriptionGroup, AddChannelToGroupViewHolder>(DiffUtilItemCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddChannelToGroupViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = AddChannelToGroupRowBinding.inflate(layoutInflater, parent, false)
        return AddChannelToGroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AddChannelToGroupViewHolder, position: Int) {
        val channelGroup = getItem(holder.bindingAdapterPosition)

        holder.binding.apply {
            groupName.text = channelGroup.name
            groupCheckbox.isChecked = channelGroup.channels.contains(channelId)

            groupCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    channelGroup.channels += channelId
                } else {
                    channelGroup.channels -= channelId
                }
            }
        }
    }
}
