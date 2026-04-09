package com.github.legiontube.ui.sheets

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.legiontube.R
import com.github.legiontube.api.obj.Subscription
import com.github.legiontube.constants.PreferenceKeys
import com.github.legiontube.databinding.SheetSubscriptionsBinding
import com.github.legiontube.extensions.toID
import com.github.legiontube.helpers.PreferenceHelper
import com.github.legiontube.ui.adapters.SubscriptionChannelAdapter
import com.github.legiontube.ui.models.EditChannelGroupsModel
import com.github.legiontube.ui.models.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class SubscriptionsBottomSheet : ExpandedBottomSheet(R.layout.sheet_subscriptions) {
    private var _binding: SheetSubscriptionsBinding? = null
    private val binding get() = _binding!!
    private val adapter = SubscriptionChannelAdapter()

    private val selectedChannelGroup
        get() = PreferenceHelper.getInt(PreferenceKeys.SELECTED_CHANNEL_GROUP, 0)

    private val searchInputText
        get() = binding.subscriptionsSearchInput.text.toString()

    private val viewModel: SubscriptionsViewModel by activityViewModels()
    private val channelGroupsModel: EditChannelGroupsModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = SheetSubscriptionsBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        binding.channelsRecycler.adapter = adapter

        binding.subscriptionsSearchInput.addTextChangedListener { _ ->
            showFilteredSubscriptions()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch(Dispatchers.IO) {
                    viewModel.fetchSubscriptions(requireContext())
                }

                launch {
                    combine(
                        viewModel.subscriptions.asFlow(),
                        channelGroupsModel.groups.asFlow(),
                    ) { subscriptions, groups ->
                        subscriptions to groups
                    }
                        .flowOn(Dispatchers.IO)
                        .collectLatest {
                            initHeaderLayout()
                            showFilteredSubscriptions()
                        }
                }
            }
        }
    }

    private fun initHeaderLayout() {
        @SuppressLint("StringFormatInvalid")
        binding.allSubsBtn.text =
            "%s (%d)".format(
                requireContext().getString(R.string.subscriptions),
                viewModel.subscriptions.value?.size ?: 0
            )
        binding.allSubsBtn.setOnClickListener {
            binding.groupEditBtn.isVisible = false

            showFilteredSubscriptions()
        }

        channelGroupsModel.groups.value?.getOrNull(selectedChannelGroup - 1)?.let { channelGroup ->
            @SuppressLint("StringFormatInvalid")
            binding.allSubsBtn.text =
                "%s (%d)".format(
                    requireContext().getString(R.string.all),
                    viewModel.subscriptions.value?.size ?: 0
                )

            binding.groupSubsBtn.isVisible = true
            binding.groupSubsBtn.isChecked = true
            binding.groupSubsBtn.text = "%s (%d)".format(
                channelGroup.name,
                channelGroup.channels.size
            )
            binding.groupSubsBtn.setOnClickListener {
                binding.groupEditBtn.isVisible = true

                showFilteredSubscriptions()
            }

            binding.groupEditBtn.isVisible = true
            binding.groupEditBtn.setOnClickListener {
                channelGroupsModel.groupToEdit = channelGroup
                EditChannelGroupSheet()
                    .show(parentFragmentManager, null)
            }

            // refresh displayed list of channels when channel groups have been edited
            if (binding.groupSubsBtn.isChecked) {
                showFilteredSubscriptions()
            }
        }
    }

    private fun showFilteredSubscriptions() {
        val loweredQuery = searchInputText.trim().lowercase()

        val shouldFilterByGroup = binding.groupSubsBtn.isChecked
        val filteredSubscriptions = viewModel.subscriptions.value.orEmpty()
            .filterByGroup(if (shouldFilterByGroup) selectedChannelGroup else 0)
            .filter { it.name.lowercase().contains(loweredQuery) }

        adapter.submitList(filteredSubscriptions)
    }

    private fun List<Subscription>.filterByGroup(groupIndex: Int): List<Subscription> {
        if (groupIndex == 0) return this

        val group = channelGroupsModel.groups.value?.getOrNull(groupIndex - 1)
            ?: return this

        return filter { group.channels.contains(it.url.toID()) }
    }
}