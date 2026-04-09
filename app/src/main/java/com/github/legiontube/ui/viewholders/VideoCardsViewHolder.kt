package com.github.legiontube.ui.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.github.legiontube.databinding.AllCaughtUpRowBinding
import com.github.legiontube.databinding.TrendingRowBinding

class VideoCardsViewHolder : RecyclerView.ViewHolder {
    var trendingRowBinding: TrendingRowBinding? = null
    var allCaughtUpBinding: AllCaughtUpRowBinding? = null

    constructor(binding: TrendingRowBinding) : super(binding.root) {
        trendingRowBinding = binding
    }

    constructor(binding: AllCaughtUpRowBinding) : super(binding.root) {
        allCaughtUpBinding = binding
    }
}
