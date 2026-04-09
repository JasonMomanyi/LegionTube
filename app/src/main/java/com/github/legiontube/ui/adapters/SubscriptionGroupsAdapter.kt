package com.github.legiontube.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.github.legiontube.R
import com.github.legiontube.databinding.SubscriptionGroupRowBinding
import com.github.legiontube.db.DatabaseHolder
import com.github.legiontube.db.obj.SubscriptionGroup
import com.github.legiontube.ui.models.EditChannelGroupsModel
import com.github.legiontube.ui.sheets.EditChannelGroupSheet
import com.github.legiontube.ui.viewholders.SubscriptionGroupsViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionGroupsAdapter(
    var groups: MutableList<SubscriptionGroup>,
    private val viewModel: EditChannelGroupsModel,
    private val parentFragmentManager: FragmentManager
) : RecyclerView.Adapter<SubscriptionGroupsViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SubscriptionGroupsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = SubscriptionGroupRowBinding.inflate(layoutInflater, parent, false)
        return SubscriptionGroupsViewHolder(binding)
    }

    override fun getItemCount() = groups.size

    override fun onBindViewHolder(holder: SubscriptionGroupsViewHolder, position: Int) {
        val subscriptionGroup = groups[position]
        holder.binding.apply {
            groupName.text = subscriptionGroup.name

            deleteGroup.setOnClickListener {
                showDeleteDialog(root.context, position)
            }

            editGroup.setOnClickListener {
                viewModel.groupToEdit = subscriptionGroup
                EditChannelGroupSheet().show(parentFragmentManager, null)
            }
        }
    }

    private fun showDeleteDialog(context: Context, position: Int) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete)
            .setMessage(R.string.irreversible)
            .setPositiveButton(R.string.okay) { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    DatabaseHolder.Database.subscriptionGroupsDao()
                        .deleteGroup(groups[position].name)

                    groups.removeAt(position)
                    viewModel.groups.postValue(groups)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
