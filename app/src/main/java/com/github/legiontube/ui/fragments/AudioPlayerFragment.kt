package com.github.legiontube.ui.fragments

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.TransitionAdapter
import androidx.core.math.MathUtils.clamp
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.github.legiontube.R
import com.github.legiontube.api.JsonHelper
import com.github.legiontube.api.obj.ChapterSegment
import com.github.legiontube.constants.IntentData
import com.github.legiontube.databinding.FragmentAudioPlayerBinding
import com.github.legiontube.enums.PlayerCommand
import com.github.legiontube.extensions.navigateVideo
import com.github.legiontube.extensions.normalize
import com.github.legiontube.extensions.seekBy
import com.github.legiontube.extensions.toID
import com.github.legiontube.extensions.togglePlayPauseState
import com.github.legiontube.extensions.updateIfChanged
import com.github.legiontube.extensions.setActionListener
import com.github.legiontube.api.MediaServiceRepository
import com.github.legiontube.api.obj.ContentItem
import com.github.legiontube.databinding.VideoRowBinding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.github.legiontube.helpers.AudioHelper
import com.github.legiontube.helpers.BackgroundHelper
import com.github.legiontube.helpers.ClipboardHelper
import com.github.legiontube.helpers.ImageHelper
import com.github.legiontube.helpers.NavigationHelper
import com.github.legiontube.helpers.PlayerHelper
import com.github.legiontube.helpers.ThemeHelper
import com.github.legiontube.services.AbstractPlayerService
import com.github.legiontube.services.OfflinePlayerService
import com.github.legiontube.services.OnlinePlayerService
import com.github.legiontube.ui.activities.AbstractPlayerHostActivity
import com.github.legiontube.ui.extensions.getSystemInsets
import com.github.legiontube.ui.extensions.setOnBackPressed
import com.github.legiontube.ui.interfaces.AudioPlayerOptions
import com.github.legiontube.ui.listeners.AudioPlayerThumbnailListener
import com.github.legiontube.ui.models.ChaptersViewModel
import com.github.legiontube.ui.models.CommonPlayerViewModel
import com.github.legiontube.ui.sheets.ChaptersBottomSheet
import com.github.legiontube.ui.sheets.PlaybackOptionsSheet
import com.github.legiontube.ui.sheets.PlayingQueueSheet
import com.github.legiontube.ui.sheets.SleepTimerSheet
import com.github.legiontube.ui.sheets.VideoOptionsBottomSheet
import com.github.legiontube.util.DataSaverMode
import com.github.legiontube.util.PlayingQueue
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@UnstableApi
class AudioPlayerFragment : Fragment(R.layout.fragment_audio_player), AudioPlayerOptions {
    private var _binding: FragmentAudioPlayerBinding? = null
    val binding get() = _binding!!

    private lateinit var audioHelper: AudioHelper
    private val activity get() = context as AbstractPlayerHostActivity
    private val viewModel: CommonPlayerViewModel by activityViewModels()
    private val chaptersModel: ChaptersViewModel by activityViewModels()

    // for the transition
    private var transitionStartId = 0
    private var transitionEndId = 0

    private var handler = Handler(Looper.getMainLooper())
    private var isPaused = !PlayerHelper.playAutomatically

    var isOffline: Boolean = false
        private set
    private var playerController: MediaController? = null
    
    private var queueListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioHelper = AudioHelper(requireContext())

        isOffline = requireArguments().getBoolean(IntentData.offlinePlayer)

        BackgroundHelper.startMediaService(
            requireContext(),
            if (isOffline) OfflinePlayerService::class.java else OnlinePlayerService::class.java,
        ) {
            if (_binding == null) {
                it.sendCustomCommand(AbstractPlayerService.stopServiceCommand, Bundle.EMPTY)
                it.release()
                return@startMediaService
            }

            playerController = it
            handleServiceConnection()
        }
    }

    @SuppressLint("ClickableViewAccessibility", "NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAudioPlayerBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        // Queue UI Setup
        val queueAdapter = com.github.legiontube.ui.adapters.PlayingQueueAdapter { videoId ->
            playerController?.navigateVideo(videoId)
        }
        binding.queueRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.queueRecyclerView.adapter = queueAdapter
        
        binding.queueRecyclerView.setActionListener(
            allowSwipe = true,
            allowDrag = true,
            onDismissedListener = { position: Int ->
                if (position == PlayingQueue.currentIndex()) {
                    queueAdapter.notifyItemChanged(position)
                } else {
                    PlayingQueue.remove(position)
                    queueAdapter.notifyItemRemoved(position)
                    queueAdapter.notifyItemRangeChanged(position, queueAdapter.itemCount)
                }
            },
            onDragListener = { from: Int, to: Int ->
                PlayingQueue.move(from, to)
                queueAdapter.notifyItemMoved(from, to)
            }
        )
        
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.queueBottomSheet)
        val clickListener = View.OnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        binding.openQueue.setOnClickListener(clickListener)
        binding.queueBottomSheetHeader.setOnClickListener(clickListener)
        
        // Empty State Setup
        if (PlayingQueue.getStreams().isEmpty()) {
            binding.playbackControlsContainer.visibility = View.GONE
            binding.queueBottomSheet.visibility = View.GONE
            binding.emptyStateSearchContainer.visibility = View.VISIBLE
        }
        
        queueListener = {
            queueAdapter.notifyDataSetChanged()
            if (PlayingQueue.getStreams().isEmpty()) {
                binding.playbackControlsContainer.visibility = View.GONE
                binding.queueBottomSheet.visibility = View.GONE
                binding.emptyStateSearchContainer.visibility = View.VISIBLE
            } else {
                binding.playbackControlsContainer.visibility = View.VISIBLE
                binding.queueBottomSheet.visibility = View.VISIBLE
                binding.emptyStateSearchContainer.visibility = View.GONE
            }
        }
        PlayingQueue.listeners.add(queueListener!!)
            
            val searchAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                var items = emptyList<ContentItem>()
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val v = VideoRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                    return object : RecyclerView.ViewHolder(v.root) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val item = items[position]
                    val b = VideoRowBinding.bind(holder.itemView)
                    ImageHelper.loadImage(item.thumbnail, b.thumbnail)
                    b.videoTitle.text = item.title
                    b.videoInfo.text = item.uploaderName
                    b.channelContainer.visibility = View.GONE
                    b.root.setOnClickListener {
                        PlayingQueue.clear()
                        playerController?.navigateVideo(item.url ?: return@setOnClickListener)
                        binding.playbackControlsContainer.visibility = View.VISIBLE
                        binding.queueBottomSheet.visibility = View.VISIBLE
                        binding.emptyStateSearchContainer.visibility = View.GONE
                    }
                }
                override fun getItemCount() = items.size
            }
            binding.searchMusicResults.layoutManager = LinearLayoutManager(requireContext())
            binding.searchMusicResults.adapter = searchAdapter
            
            binding.searchMusicInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = binding.searchMusicInput.text.toString()
                    if (query.isNotEmpty()) {
                        binding.searchMusicProgress.visibility = View.VISIBLE
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val result = MediaServiceRepository.instance.getSearchResults(query, "music_songs")
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    searchAdapter.items = result.items
                                    searchAdapter.notifyDataSetChanged()
                                    binding.searchMusicProgress.visibility = View.GONE
                                }
                            } catch (e: Exception) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    binding.searchMusicProgress.visibility = View.GONE
                                }
                            }
                        }
                    }
                    true
                } else false
            }

        // manually apply additional padding for edge-to-edge compatibility
        activity.getSystemInsets()?.let { systemBars ->
            with(binding.audioPlayerMain) {
                setPadding(
                    paddingLeft,
                    paddingTop + systemBars.top,
                    paddingRight,
                    paddingBottom + systemBars.bottom
                )
            }
        }

        initializeTransitionLayout()

        // select the title TV in order for it to automatically scroll
        binding.title.isSelected = true
        binding.uploader.isSelected = true

        binding.title.setOnLongClickListener {
            ClipboardHelper.save(requireContext(), text = binding.title.text.toString())
            true
        }

        binding.minimizePlayer.setOnClickListener {
            activity.minimizePlayerContainerLayout()
            binding.playerMotionLayout.transitionToEnd()
        }

        binding.autoPlay.isChecked = PlayerHelper.autoPlayEnabled
        binding.autoPlay.setOnCheckedChangeListener { _, isChecked ->
            PlayerHelper.autoPlayEnabled = isChecked
        }

        binding.prev.setOnClickListener {
            playerController?.navigateVideo(PlayingQueue.getPrev() ?: return@setOnClickListener)
        }

        binding.next.setOnClickListener {
            playerController?.navigateVideo(PlayingQueue.getNext() ?: return@setOnClickListener)
        }

        binding.rewindBTN.setOnClickListener {
            playerController?.seekBy(-PlayerHelper.seekIncrement)
        }
        binding.forwardBTN.setOnClickListener {
            playerController?.seekBy(PlayerHelper.seekIncrement)
        }

        childFragmentManager.setFragmentResultListener(
            PlayingQueueSheet.PLAYING_QUEUE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, args ->
            playerController?.navigateVideo(
                args.getString(IntentData.videoId) ?: return@setFragmentResultListener
            )
        }


        binding.playbackOptions.setOnClickListener {
            playerController?.let {
                PlaybackOptionsSheet(it)
                    .show(childFragmentManager)
            }
        }

        binding.sleepTimer.setOnClickListener {
            SleepTimerSheet().show(childFragmentManager)
        }

        binding.openVideo.setOnClickListener {
            val currentId = PlayingQueue.getCurrent()?.url?.toID()
            switchToVideoMode(currentId ?: return@setOnClickListener)
        }

        childFragmentManager.setFragmentResultListener(
            ChaptersBottomSheet.SEEK_TO_POSITION_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            playerController?.seekTo(bundle.getLong(IntentData.currentPosition))
        }

        chaptersModel.chaptersLiveData.observe(viewLifecycleOwner) { chapters ->
            _binding?.openChapters?.isVisible = !chapters.isNullOrEmpty()
        }

        binding.openChapters.setOnClickListener {
            ChaptersBottomSheet()
                .apply {
                    arguments = bundleOf(
                        IntentData.duration to playerController?.duration?.div(1000)
                    )
                }
                .show(childFragmentManager)
        }

        binding.miniPlayerClose.setOnClickListener {
            killFragment(true)
        }

        val listener = AudioPlayerThumbnailListener(requireContext(), this)
        binding.thumbnail.setOnTouchListener(listener)

        binding.playPause.setOnClickListener {
            playerController?.togglePlayPauseState()
        }

        binding.miniPlayerPause.setOnClickListener {
            playerController?.togglePlayPauseState()
        }

        binding.showMore.setOnClickListener {
            onLongTap()
        }

        // update the currently shown volume
        binding.volumeProgressBar.let { bar ->
            bar.progress = audioHelper.getVolumeWithScale(bar.max)
        }

        if (!PlayerHelper.playAutomatically) updatePlayPauseButton()

        updateChapterIndex()

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                binding.audioPlayerContainer.isClickable = false
                binding.playerMotionLayout.transitionToEnd()
                activity.minimizePlayerContainerLayout()
                activity.requestOrientationChange()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                binding.playerMotionLayout.progress = backEvent.progress
            }

            override fun handleOnBackCancelled() {
                binding.playerMotionLayout.transitionToStart()
            }
        }
        setOnBackPressed(onBackPressedCallback)

        viewModel.isMiniPlayerVisible.observe(viewLifecycleOwner) { isMiniPlayerVisible ->
            // re-add the callback on top of the back pressed dispatcher listeners stack,
            // so that it's the first one to become called while the full player is visible
            if (!isMiniPlayerVisible) {
                onBackPressedCallback.remove()
                setOnBackPressed(onBackPressedCallback)
            }

            // if the player is minimized, the fragment behind the player should handle the event
            onBackPressedCallback.isEnabled = isMiniPlayerVisible != true
        }
    }

    override fun onResume() {
        super.onResume()
        PlayerHelper.globalAudioOnlyMode = true
    }

    override fun onPause() {
        super.onPause()
        PlayerHelper.globalAudioOnlyMode = false
    }

    fun switchToVideoMode(videoId: String) {
        playerController?.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            bundleOf(PlayerCommand.TOGGLE_AUDIO_ONLY_MODE.name to false)
        )

        killFragment(false)

        NavigationHelper.openVideoPlayerFragment(
            context = requireContext(),
            videoId = videoId,
            isOffline = isOffline,
            alreadyStarted = true,
        )
    }

    private fun killFragment(stopPlayer: Boolean) {
        viewModel.isMiniPlayerVisible.value = false

        if (stopPlayer) playerController?.sendCustomCommand(
            AbstractPlayerService.stopServiceCommand,
            Bundle.EMPTY
        )
        playerController?.release()
        playerController = null

        viewModel.isFullscreen.value = false
        binding.playerMotionLayout.transitionToEnd()
        activity.supportFragmentManager.commit {
            remove(this@AudioPlayerFragment)
        }
    }

    fun playNextVideo(videoId: String) {
        playerController?.navigateVideo(videoId)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeTransitionLayout() {
        activity.setPlayerContainerProgress(0f)

        binding.playerMotionLayout.addTransitionListener(object : TransitionAdapter() {
            override fun onTransitionChange(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int,
                progress: Float
            ) {
                activity.setPlayerContainerProgress(progress.absoluteValue)
                transitionEndId = endId
                transitionStartId = startId
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                if (currentId == transitionEndId) {
                    viewModel.isMiniPlayerVisible.value = true
                    activity.minimizePlayerContainerLayout()
                } else if (currentId == transitionStartId) {
                    viewModel.isMiniPlayerVisible.value = false
                    activity.maximizePlayerContainerLayout()
                }
            }
        })

        if (arguments?.getBoolean(IntentData.minimizeByDefault, false) != true) {
            binding.playerMotionLayout.progress = 1f
            binding.playerMotionLayout.transitionToStart()
        } else {
            binding.playerMotionLayout.progress = 0f
            binding.playerMotionLayout.transitionToEnd()
        }
    }

    /**
     * Load the information from a new stream into the UI
     */
    private fun updateStreamInfo(metadata: MediaMetadata) {
        val binding = _binding ?: return

        binding.title.text = metadata.title
        binding.miniPlayerTitle.text = metadata.title

        binding.uploader.text = metadata.artist
        binding.uploader.setOnClickListener {
            val uploaderId = metadata.composer?.toString() ?: return@setOnClickListener
            NavigationHelper.navigateChannel(requireContext(), uploaderId)
        }

        metadata.artworkUri?.let { updateThumbnailAsync(it) }

        initializeSeekBar()
    }

    private fun updateThumbnailAsync(thumbnailUri: Uri) {
        if (DataSaverMode.isEnabled(requireContext()) && !isOffline) {
            binding.progress.isVisible = false
            binding.thumbnail.setImageResource(R.mipmap.ic_launcher_foreground)
            val primaryColor = ThemeHelper.getThemeColor(
                requireContext(),
                androidx.appcompat.R.attr.colorPrimary
            )
            binding.thumbnail.setColorFilter(primaryColor)
            return
        }

        binding.progress.isVisible = true
        binding.thumbnail.isGone = true
        // reset color filter if data saver mode got toggled or conditions for it changed
        binding.thumbnail.setColorFilter(Color.TRANSPARENT)

        lifecycleScope.launch {
            val binding = _binding ?: return@launch
            val bitmap = ImageHelper.getImage(requireContext(), thumbnailUri)
            binding.thumbnail.setImageBitmap(bitmap)
            binding.miniPlayerThumbnail.setImageBitmap(bitmap)
            binding.thumbnail.isVisible = true
            binding.progress.isGone = true
        }
    }

    private fun initializeSeekBar() {
        binding.timeBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) playerController?.seekTo(value.toLong() * 1000)
        }
        updateSeekBar()
    }

    /**
     * Update the position, duration and text views belonging to the seek bar
     */
    private fun updateSeekBar() {
        val binding = _binding ?: return
        val duration = playerController?.duration?.takeIf { it > 0 } ?: let {
            // if there's no duration available, clear everything
            binding.timeBar.value = 0f
            binding.duration.text = ""
            binding.currentPosition.text = ""
            handler.postDelayed(this::updateSeekBar, 100)
            return
        }
        val currentPosition = playerController?.currentPosition?.toFloat() ?: 0f

        // set the text for the indicators
        binding.duration.text = DateUtils.formatElapsedTime(duration / 1000)
        binding.currentPosition.text = DateUtils.formatElapsedTime(
            (currentPosition / 1000).toLong()
        )

        // update the time bar current value and maximum value
        binding.timeBar.valueTo = (duration / 1000).toFloat()
        binding.timeBar.value = clamp(
            currentPosition / 1000,
            binding.timeBar.valueFrom,
            binding.timeBar.valueTo
        )

        handler.postDelayed(this::updateSeekBar, 200)
    }

    private fun updatePlayPauseButton() {
        playerController?.let {
            val binding = _binding ?: return

            val iconRes = PlayerHelper.getPlayPauseActionIcon(it)
            binding.playPause.setIconResource(iconRes)
            binding.miniPlayerPause.setImageResource(iconRes)
        }
    }

    private fun handleServiceConnection() {
        playerController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                updatePlayPauseButton()
                isPaused = !isPlaying
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)

                updateStreamInfo(mediaMetadata)
                // JSON-encode as work-around for https://github.com/androidx/media/issues/564
                val chapters: List<ChapterSegment>? =
                    mediaMetadata.extras?.getString(IntentData.chapters)?.let {
                        JsonHelper.json.decodeFromString(it)
                    }
                chaptersModel.chaptersLiveData.value = chapters
            }
        })
        playerController?.mediaMetadata?.let { updateStreamInfo(it) }
        // JSON-encode as work-around for https://github.com/androidx/media/issues/564
        chaptersModel.chaptersLiveData.value =
            playerController?.mediaMetadata?.extras?.getString(IntentData.chapters)?.let {
                JsonHelper.json.decodeFromString(it)
            }

        initializeSeekBar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        queueListener?.let { PlayingQueue.listeners.remove(it) }
        queueListener = null
        _binding = null
    }

    override fun onSingleTap() {
        playerController?.togglePlayPauseState()
    }

    override fun onLongTap() {
        val current = PlayingQueue.getCurrent() ?: return
        VideoOptionsBottomSheet()
            .apply {
                arguments = bundleOf(IntentData.streamItem to current)
            }
            .show(childFragmentManager)
    }

    override fun onSwipe(distanceY: Float) {
        if (!PlayerHelper.swipeGestureEnabled) return

        binding.volumeControls.isVisible = true
        updateVolume(distanceY)
    }

    override fun onSwipeEnd() {
        if (!PlayerHelper.swipeGestureEnabled) return

        binding.volumeControls.isGone = true
    }

    private fun updateVolume(distance: Float) {
        val bar = binding.volumeProgressBar
        binding.volumeControls.apply {
            if (isGone) {
                isVisible = true
                // Volume could be changed using other mediums, sync progress
                // bar with new value.
                bar.progress = audioHelper.getVolumeWithScale(bar.max)
            }
        }

        if (bar.progress == 0) {
            binding.volumeImageView.setImageResource(
                when {
                    distance > 0 -> R.drawable.ic_volume_up
                    else -> R.drawable.ic_volume_off
                }
            )
        }
        bar.incrementProgressBy(distance.toInt() / 3)
        audioHelper.setVolumeWithScale(bar.progress, bar.max)

        binding.volumeTextView.text = "${bar.progress.normalize(0, bar.max, 0, 100)}"
    }

    private fun updateChapterIndex() {
        if (_binding == null) return
        handler.postDelayed(this::updateChapterIndex, 100)

        val currentIndex =
            PlayerHelper.getCurrentChapterIndex(
                playerController?.currentPosition ?: return,
                chaptersModel.chapters
            )
        chaptersModel.currentChapterIndex.updateIfChanged(currentIndex ?: return)
    }
}
