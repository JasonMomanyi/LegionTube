package com.github.legiontube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.github.legiontube.R
import com.github.legiontube.constants.PreferenceKeys
import com.github.legiontube.databinding.AppIconItemBinding
import com.github.legiontube.helpers.PreferenceHelper
import com.github.legiontube.helpers.ThemeHelper
import com.github.legiontube.ui.viewholders.IconsSheetViewHolder

class IconsSheetAdapter : RecyclerView.Adapter<IconsSheetViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconsSheetViewHolder {
        val binding = AppIconItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IconsSheetViewHolder(binding)
    }

    override fun getItemCount() = availableIcons.size

    override fun onBindViewHolder(holder: IconsSheetViewHolder, position: Int) {
        val appIcon = availableIcons[position]
        holder.binding.apply {
            iconIV.setImageResource(appIcon.iconResource)
            iconName.text = root.context.getString(appIcon.nameResource)
            root.setOnClickListener {
                PreferenceHelper.putString(PreferenceKeys.APP_ICON, appIcon.activityAlias)
                // App icon switching disabled as we only have one icon now
                // ThemeHelper.changeIcon(root.context, appIcon.activityAlias)
            }
        }
    }

    companion object {
        sealed class AppIcon(
            @StringRes val nameResource: Int,
            @DrawableRes val iconResource: Int,
            val activityAlias: String
        ) {
            object Default :
                AppIcon(R.string.defaultIcon, R.mipmap.ic_launcher, "Default")
        }

        val availableIcons = listOf(
            AppIcon.Default
        )
    }
}
