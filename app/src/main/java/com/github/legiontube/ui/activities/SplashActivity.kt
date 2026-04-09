package com.github.legiontube.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.github.legiontube.R
import com.github.legiontube.constants.PreferenceKeys
import com.github.legiontube.helpers.PreferenceHelper

/**
 * LegionTube branded splash screen with video playback.
 *
 * Features:
 *   - Plays splash_video.mp4 from res/raw
 *   - "Skip" button lets users skip immediately
 *   - "Don't show again" checkbox remembers the choice
 *   - If remembered, goes straight to MainActivity on next launch
 *   - Falls back to static splash if video can't play
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val FALLBACK_DURATION = 2500L // static splash timeout
    }

    private var navigated = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Check if user chose to skip splash
        if (shouldSkipSplash()) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_splash)

        val videoView = findViewById<VideoView>(R.id.splash_video)
        val skipButton = findViewById<TextView>(R.id.skip_button)
        val rememberCheckbox = findViewById<CheckBox>(R.id.remember_skip)
        val splashSubtitle = findViewById<TextView>(R.id.splash_subtitle)

        // Apply animations to static elements
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.splash_slide_up)

        splashSubtitle?.startAnimation(slideUp)

        // Setup skip button
        skipButton?.setOnClickListener {
            handleSkip(rememberCheckbox)
        }

        // Try to play splash video
        try {
            val videoUri = Uri.parse(
                "android.resource://${packageName}/${R.raw.splash_video}"
            )
            videoView?.apply {
                setVideoURI(videoUri)
                setOnPreparedListener { mp ->
                    mp.isLooping = false
                    // Scale video to fill
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    start()

                    // Show skip controls after 1 second
                    handler.postDelayed({
                        skipButton?.visibility = View.VISIBLE
                        rememberCheckbox?.visibility = View.VISIBLE
                        skipButton?.startAnimation(
                            AnimationUtils.loadAnimation(this@SplashActivity, R.anim.splash_slide_up)
                        )
                        rememberCheckbox?.startAnimation(
                            AnimationUtils.loadAnimation(this@SplashActivity, R.anim.splash_slide_up)
                        )
                    }, 1000)
                }

                setOnCompletionListener {
                    // Video finished playing naturally
                    if (!navigated) {
                        navigateToMain()
                    }
                }

                setOnErrorListener { _, _, _ ->
                    // Video playback failed — use static fallback
                    visibility = View.GONE
                    useFallbackSplash(skipButton, rememberCheckbox)
                    true
                }
            }
        } catch (e: Exception) {
            // Resource not found or other issue — use static fallback
            videoView?.visibility = View.GONE
            useFallbackSplash(skipButton, rememberCheckbox)
        }
    }

    private fun shouldSkipSplash(): Boolean {
        return try {
            PreferenceHelper.getBoolean(PreferenceKeys.SKIP_SPLASH, false)
        } catch (e: Exception) {
            // PreferenceHelper may not be initialized yet on very first launch
            false
        }
    }

    private fun handleSkip(rememberCheckbox: CheckBox?) {
        if (rememberCheckbox?.isChecked == true) {
            try {
                PreferenceHelper.putBoolean(PreferenceKeys.SKIP_SPLASH, true)
            } catch (e: Exception) {
                // Ignore if preferences aren't available
            }
        }
        navigateToMain()
    }

    private fun useFallbackSplash(skipButton: TextView?, rememberCheckbox: CheckBox?) {
        // Show skip controls immediately for static splash
        skipButton?.visibility = View.VISIBLE
        rememberCheckbox?.visibility = View.VISIBLE

        // Auto-navigate after fallback duration
        handler.postDelayed({
            if (!navigated) {
                navigateToMain()
            }
        }, FALLBACK_DURATION)
    }

    private fun navigateToMain() {
        if (navigated) return
        navigated = true

        handler.removeCallbacksAndMessages(null)

        val intent = Intent(this, MainActivity::class.java)
        // Forward any intent extras (e.g., deep links)
        this.intent?.extras?.let { intent.putExtras(it) }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
