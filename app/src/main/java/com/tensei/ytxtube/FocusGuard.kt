package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Focus Guard: a daily watch budget and break reminders. When the budget is used up, or you have
 * watched for a long stretch without a break, playback pauses and a message explains why. Each time
 * you carry on after the daily limit you get a short grace period, so it nudges rather than locks you out.
 */
data class FocusConfig(
    val enabled: Boolean = false,
    val dailyBudgetMin: Int = 120,
    val breakEveryMin: Int = 45,
    val breakNudges: Boolean = true,
    val graceMin: Int = 5,
)

data class FocusState(
    val day: String = "",
    val secondsToday: Int = 0,
    val extraSeconds: Int = 0,
    val continuousSeconds: Int = 0,
    val idleSeconds: Int = 0,
)

sealed class FocusAction {
    data object None : FocusAction()
    data class BreakDue(val minutes: Int) : FocusAction()
    data class BudgetReached(val budgetMin: Int) : FocusAction()
}

/** Pure rules (unit-tested). */
object FocusLogic {
    /** Not watching for this long counts as a break. */
    const val BREAK_GAP_SEC = 120

    fun dayKey(ms: Long, tz: TimeZone): String {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = ms
        return String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Moves the counters on by [deltaSec], starting fresh on a new day. */
    fun advance(state: FocusState, day: String, playing: Boolean, deltaSec: Int): FocusState {
        val base = if (state.day != day) FocusState(day = day) else state
        return if (playing) {
            base.copy(
                secondsToday = base.secondsToday + deltaSec,
                continuousSeconds = base.continuousSeconds + deltaSec,
                idleSeconds = 0,
            )
        } else {
            val idle = base.idleSeconds + deltaSec
            base.copy(idleSeconds = idle, continuousSeconds = if (idle >= BREAK_GAP_SEC) 0 else base.continuousSeconds)
        }
    }

    fun budgetSeconds(state: FocusState, config: FocusConfig): Int = config.dailyBudgetMin * 60 + state.extraSeconds

    fun remainingSeconds(state: FocusState, config: FocusConfig): Int =
        (budgetSeconds(state, config) - state.secondsToday).coerceAtLeast(0)

    /** The budget takes priority over a break nudge. */
    fun evaluate(state: FocusState, config: FocusConfig): FocusAction = when {
        !config.enabled -> FocusAction.None
        state.secondsToday >= budgetSeconds(state, config) -> FocusAction.BudgetReached(config.dailyBudgetMin)
        config.breakNudges && state.continuousSeconds >= config.breakEveryMin * 60 ->
            FocusAction.BreakDue(state.continuousSeconds / 60)
        else -> FocusAction.None
    }

    /** What the counters become once an action has been carried out. */
    fun afterAction(state: FocusState, action: FocusAction, config: FocusConfig): FocusState = when (action) {
        is FocusAction.BreakDue -> state.copy(continuousSeconds = 0)
        is FocusAction.BudgetReached -> state.copy(extraSeconds = state.extraSeconds + config.graceMin * 60)
        FocusAction.None -> state
    }
}

object FocusGuard {
    private const val PREFS = "focus_guard"
    private var prefs: SharedPreferences? = null
    private var lastSavedElapsed = 0L

    private val _config = MutableStateFlow(FocusConfig())
    val config: StateFlow<FocusConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        _config.value = FocusConfig(
            enabled = p.getBoolean("enabled", false),
            dailyBudgetMin = p.getInt("budget", 120).coerceIn(15, 360),
            breakEveryMin = p.getInt("break", 45).coerceIn(10, 120),
            breakNudges = p.getBoolean("nudges", true),
            graceMin = p.getInt("grace", 5).coerceIn(1, 15),
        )
        val today = FocusLogic.dayKey(System.currentTimeMillis(), TimeZone.getDefault())
        val saved = FocusState(
            day = p.getString("day", "").orEmpty(),
            secondsToday = p.getInt("secs", 0),
            extraSeconds = p.getInt("extra", 0),
        )
        _state.value = if (saved.day == today) saved else FocusState(day = today)
    }

    fun update(transform: (FocusConfig) -> FocusConfig) {
        val next = transform(_config.value).let {
            it.copy(
                dailyBudgetMin = it.dailyBudgetMin.coerceIn(15, 360),
                breakEveryMin = it.breakEveryMin.coerceIn(10, 120),
                graceMin = it.graceMin.coerceIn(1, 15),
            )
        }
        _config.value = next
        prefs?.edit()
            ?.putBoolean("enabled", next.enabled)?.putInt("budget", next.dailyBudgetMin)
            ?.putInt("break", next.breakEveryMin)?.putBoolean("nudges", next.breakNudges)
            ?.putInt("grace", next.graceMin)?.apply()
    }

    /** Gives extra time today (used by the "+15 min" button). */
    fun addTime(minutes: Int) {
        _state.value = _state.value.copy(extraSeconds = _state.value.extraSeconds + minutes * 60)
        persist(force = true)
    }

    fun resetToday() {
        _state.value = FocusState(day = _state.value.day)
        persist(force = true)
    }

    /** Called every few seconds by [FocusGuardTicker] on the main thread. */
    fun tick(context: Context, player: Player, deltaSec: Int) {
        val cfg = _config.value
        val playing = player.isPlaying
        val day = FocusLogic.dayKey(System.currentTimeMillis(), TimeZone.getDefault())
        var next = FocusLogic.advance(_state.value, day, playing, deltaSec)
        val action = FocusLogic.evaluate(next, cfg)
        if (playing && action != FocusAction.None) {
            player.pause()
            next = FocusLogic.afterAction(next, action, cfg)
            val message = when (action) {
                is FocusAction.BreakDue -> "You have been watching for ${action.minutes} min. Time for a break."
                is FocusAction.BudgetReached -> "Daily watch limit of ${action.budgetMin} min reached. Playing on gives you ${cfg.graceMin} more min."
                FocusAction.None -> ""
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        _state.value = next
        persist(force = action != FocusAction.None)
    }

    private fun persist(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSavedElapsed < 30_000L) return
        lastSavedElapsed = now
        val s = _state.value
        prefs?.edit()?.putString("day", s.day)?.putInt("secs", s.secondsToday)?.putInt("extra", s.extraSeconds)?.apply()
    }
}

/** Drives [FocusGuard.tick] while the playback service is alive. */
class FocusGuardTicker(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(5_000L)
                val now = SystemClock.elapsedRealtime()
                val delta = ((now - last) / 1000L).toInt().coerceIn(0, 30)
                last = now
                FocusGuard.tick(context, player, delta)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

@Composable
fun FocusGuardSheet(onDismiss: () -> Unit) {
    val config by FocusGuard.config.collectAsState()
    val state by FocusGuard.state.collectAsState()
    val budgetSec = FocusLogic.budgetSeconds(state, config)
    val progress = if (budgetSec <= 0) 0f else (state.secondsToday.toFloat() / budgetSec).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Focus guard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) { Text("Done") }
        }
        YtxNote("Pauses playback when you hit your daily limit or watch too long without a break. Counted on this phone only.")

        YtxPanel {
            YtxSwitchRow(
                title = "Turn on focus guard",
                subtitle = "Applies to video and music played in YTX",
                checked = config.enabled,
                onChecked = { on -> FocusGuard.update { it.copy(enabled = on) } },
            )
        }

        YtxSectionLabel("Today")
        YtxPanel {
            val used = WatchInsightsLogic.formatDuration(state.secondsToday)
            val left = WatchInsightsLogic.formatDuration(FocusLogic.remainingSeconds(state, config))
            Text("$used watched · $left left", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Daily watch budget used: ${(progress * 100).toInt()} percent" },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YtxChip(label = "+15 min today", selected = false) { FocusGuard.addTime(15) }
                YtxChip(label = "Reset today", selected = false) { FocusGuard.resetToday() }
            }
        }

        YtxSectionLabel("Limits")
        YtxPanel {
            YtxSliderRow(
                label = "Daily watch budget",
                value = config.dailyBudgetMin.toFloat(),
                range = 15f..360f,
                steps = 22,
                readout = WatchInsightsLogic.formatDuration(config.dailyBudgetMin * 60),
            ) { v -> FocusGuard.update { it.copy(dailyBudgetMin = v.toInt()) } }
            YtxSwitchRow(
                title = "Break reminders",
                subtitle = "Pause after a long stretch without stopping",
                checked = config.breakNudges,
                onChecked = { on -> FocusGuard.update { it.copy(breakNudges = on) } },
            )
            YtxSliderRow(
                label = "Remind me every",
                value = config.breakEveryMin.toFloat(),
                range = 10f..120f,
                steps = 21,
                readout = "${config.breakEveryMin} min",
            ) { v -> FocusGuard.update { it.copy(breakEveryMin = v.toInt()) } }
            YtxSliderRow(
                label = "Extra time after the limit",
                value = config.graceMin.toFloat(),
                range = 1f..15f,
                steps = 13,
                readout = "${config.graceMin} min",
            ) { v -> FocusGuard.update { it.copy(graceMin = v.toInt()) } }
        }
    }
}
