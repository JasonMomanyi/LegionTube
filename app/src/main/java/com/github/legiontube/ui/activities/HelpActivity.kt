package com.github.legiontube.ui.activities

import android.os.Bundle
import com.github.legiontube.databinding.ActivityHelpBinding
import com.github.legiontube.helpers.IntentHelper
import com.github.legiontube.ui.base.BaseActivity
import com.google.android.material.card.MaterialCardView

class HelpActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupCard(binding.faq, FAQ_URL)
        setupCard(binding.matrix, MATRIX_URL)
        setupCard(binding.mastodon, MASTODON_URL)
        setupCard(binding.lemmy, LEMMY_URL)
    }

    private fun setupCard(card: MaterialCardView, link: String) {
        card.setOnClickListener {
            IntentHelper.openLinkFromHref(this, supportFragmentManager, link)
        }
    }

    companion object {
        private const val FAQ_URL = "https://github.com/JasonMomanyi"
        private const val MATRIX_URL = "https://github.com/JasonMomanyi"
        private const val MASTODON_URL = "https://github.com/JasonMomanyi"
        private const val LEMMY_URL = "https://github.com/JasonMomanyi"
    }
}
