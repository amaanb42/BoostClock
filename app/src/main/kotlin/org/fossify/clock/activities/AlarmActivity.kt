package org.fossify.clock.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.fossify.clock.R
import org.fossify.clock.databinding.ActivityAlarmBinding
import org.fossify.clock.extensions.alarmController
import org.fossify.clock.extensions.config
import org.fossify.clock.extensions.dbHelper
import org.fossify.clock.extensions.getFormattedTime
import org.fossify.clock.helpers.ALARM_ID
import org.fossify.clock.helpers.getPassedSeconds
import org.fossify.clock.models.Alarm
import org.fossify.clock.models.AlarmEvent
import org.fossify.clock.voice.RecognitionState
import org.fossify.clock.voice.VoiceActionGate
import org.fossify.clock.voice.VoiceAlarmAction
import org.fossify.clock.voice.VoiceCapability
import org.fossify.clock.voice.VoiceRecognitionManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.showPickSecondsDialog
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.MINUTE_SECONDS
import org.fossify.commons.helpers.isOreoMr1Plus
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.max
import kotlin.math.min

@Suppress("TooManyFunctions")
class AlarmActivity : SimpleActivity() {
    companion object {
        private const val REMINDER_DRAGGABLE_BACKGROUND_ALPHA = 0.2f
        private const val REMINDER_GUIDE_SHOW_DURATION = 2000L
        private const val DRAG_ACTION_THRESHOLD_PX = 50f
    }

    private val swipeGuideFadeHandler = Handler(Looper.getMainLooper())
    private var alarm: Alarm? = null
    private var didVibrate = false
    private var dragDownX = 0f
    private val actionGate = VoiceActionGate()
    private var voiceGeneration = 0L
    private var activityResumed = false

    private val binding by viewBinding(ActivityAlarmBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        showOverLockscreen()
        updateTextColors(binding.root)

        val id = intent.getIntExtra(ALARM_ID, -1)
        alarm = dbHelper.getAlarmWithId(id)
        if (alarm == null) {
            finish()
            return
        }

        val label = alarm!!.label.ifEmpty {
            getString(org.fossify.commons.R.string.alarm)
        }

        binding.reminderTitle.text = label
        binding.reminderText.text = getFormattedTime(
            passedSeconds = getPassedSeconds(),
            showSeconds = false,
            makeAmPmSmaller = false
        )

        setupAlarmButtons()
        observeVoiceState()
        EventBus.getDefault().register(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAlarmButtons() {
        binding.reminderDraggableBackground.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.pulsing_animation)
        )
        binding.reminderDraggableBackground.applyColorFilter(getProperPrimaryColor())

        val textColor = getProperTextColor()
        binding.reminderDismiss.applyColorFilter(textColor)
        binding.reminderDraggable.applyColorFilter(textColor)
        binding.reminderSnooze.applyColorFilter(textColor)

        var minDragX = 0f
        var maxDragX = 0f
        var initialDraggableX = 0f

        binding.reminderDismiss.onGlobalLayout {
            minDragX = binding.reminderSnooze.left.toFloat()
            maxDragX = binding.reminderDismiss.left.toFloat()
            initialDraggableX = binding.reminderDraggable.left.toFloat()
        }

        binding.reminderDraggable.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    binding.reminderDraggableBackground.animate().alpha(0f)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    if (!didVibrate) {
                        binding.reminderDraggable.animate().x(initialDraggableX).withEndAction {
                            binding.reminderDraggableBackground
                                .animate()
                                .alpha(REMINDER_DRAGGABLE_BACKGROUND_ALPHA)
                        }

                        binding.reminderGuide.animate().alpha(1f).start()
                        swipeGuideFadeHandler.removeCallbacksAndMessages(null)
                        swipeGuideFadeHandler.postDelayed({
                            binding.reminderGuide.animate().alpha(0f).start()
                        }, REMINDER_GUIDE_SHOW_DURATION)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    binding.reminderDraggable.x = min(
                        a = maxDragX,
                        b = max(minDragX, event.rawX - dragDownX)
                    )

                    if (binding.reminderDraggable.x >= maxDragX - DRAG_ACTION_THRESHOLD_PX) {
                        if (!didVibrate) {
                            binding.reminderDraggable.performHapticFeedback()
                            didVibrate = true
                            runTouchAction { dismissAlarmAndFinish() }
                        }
                    } else if (binding.reminderDraggable.x <= minDragX + DRAG_ACTION_THRESHOLD_PX) {
                        if (!didVibrate) {
                            binding.reminderDraggable.performHapticFeedback()
                            didVibrate = true
                            runTouchAction { snoozeAlarm() }
                        }
                    }
                }
            }
            true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupAlarmButtons()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        actionGate.invalidate()
        VoiceRecognitionManager.stopListening()
        when (intent.action) {
            AlarmClock.ACTION_DISMISS_ALARM -> runTouchAction { dismissAlarmAndFinish() }
            AlarmClock.ACTION_SNOOZE_ALARM -> {
                val durationMinutes = intent.getIntExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, -1)
                if (durationMinutes == -1) {
                    runTouchAction { snoozeAlarm() }
                } else {
                    runTouchAction { snoozeAlarm(durationMinutes) }
                }
            }

            else -> {
                // no-op. user probably clicked the notification
            }
        }
    }

    override fun onDestroy() {
        activityResumed = false
        actionGate.invalidate()
        VoiceRecognitionManager.stopListening()
        swipeGuideFadeHandler.removeCallbacksAndMessages(null)
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        voiceGeneration = actionGate.newGeneration()
        if (isVoiceEnabled()) {
            binding.voiceStatusHolder.visibility = android.view.View.VISIBLE
            VoiceRecognitionManager.prepare(applicationContext)
            startVoiceCaptureIfReady(VoiceRecognitionManager.state.value)
        } else {
            binding.voiceStatusHolder.visibility = android.view.View.GONE
        }
    }

    override fun onPause() {
        activityResumed = false
        actionGate.invalidate()
        VoiceRecognitionManager.stopListening()
        super.onPause()
    }

    private fun snoozeAlarm(overrideSnoozeDuration: Int? = null) {
        if (overrideSnoozeDuration != null) {
            dismissAlarmAndFinish(overrideSnoozeDuration)
        } else if (config.useSameSnooze) {
            dismissAlarmAndFinish(config.snoozeTime)
        } else {
            VoiceRecognitionManager.stopListening()
            alarmController.silenceAlarm(alarm!!.id)
            showPickSecondsDialog(
                curSeconds = config.snoozeTime * MINUTE_SECONDS,
                isSnoozePicker = true,
                cancelCallback = {
                    dismissAlarmAndFinish()
                },
                callback = {
                    config.snoozeTime = it / MINUTE_SECONDS
                    dismissAlarmAndFinish(config.snoozeTime)
                }
            )
        }
    }

    private fun dismissAlarmAndFinish(snoozeMinutes: Int = -1) {
        VoiceRecognitionManager.stopListening()
        if (alarm != null) {
            if (snoozeMinutes != -1) {
                alarmController.snoozeAlarm(alarm!!.id, snoozeMinutes)
            } else {
                alarmController.stopAlarm(alarm!!.id)
            }
        }

        finishActivity()
    }

    private fun runTouchAction(action: () -> Unit) {
        if (actionGate.tryClaimTouch()) action()
    }

    private fun observeVoiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                VoiceRecognitionManager.state.collect { state ->
                    updateVoiceState(state)
                    startVoiceCaptureIfReady(state)
                }
            }
        }
    }

    private fun startVoiceCaptureIfReady(state: RecognitionState) {
        if (!activityResumed || !isVoiceEnabled() || state != RecognitionState.READY) return
        val callbackGeneration = voiceGeneration
        VoiceRecognitionManager.startListening { command ->
            runOnUiThread {
                if (!activityResumed || !actionGate.tryClaim(callbackGeneration)) return@runOnUiThread
                VoiceRecognitionManager.stopListening()
                when (val action = VoiceAlarmAction.forCommand(command, config.snoozeTime)) {
                    VoiceAlarmAction.Dismiss -> dismissAlarmAndFinish()
                    is VoiceAlarmAction.Snooze -> dismissAlarmAndFinish(action.durationMinutes)
                }
            }
        }.onFailure {
            VoiceRecognitionManager.stopListening(unavailable = true)
        }
    }

    private fun updateVoiceState(state: RecognitionState) {
        if (!isVoiceEnabled()) return
        val stringId = when (state) {
            RecognitionState.LOADING -> R.string.voice_loading
            RecognitionState.READY,
            RecognitionState.LISTENING -> R.string.voice_listening
            RecognitionState.RECOGNIZED -> R.string.voice_recognized
            RecognitionState.UNAVAILABLE,
            RecognitionState.DISABLED -> R.string.voice_unavailable
        }
        binding.voiceStatusText.setText(stringId)
        binding.voiceStatusIcon.applyColorFilter(getProperTextColor())
    }

    private fun isVoiceEnabled(): Boolean =
        config.voiceControlEnabled &&
            VoiceCapability.detect(this).supported &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun showOverLockscreen() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAlarmStoppedEvent(event: AlarmEvent.Stopped) {
        if (event.alarmId == alarm?.id && !isFinishing) {
            finishActivity()
        }
    }

    private fun finishActivity() {
        finish()
        overridePendingTransition(0, 0)
    }
}
