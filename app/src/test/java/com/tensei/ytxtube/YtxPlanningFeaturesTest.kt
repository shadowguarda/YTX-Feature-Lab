package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Pure-logic tests for Watch Plan, Watch Insights, Focus Guard, Chapter Autopilot and Time-Fit Queue. */
class YtxPlanningFeaturesTest {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long {
        val c = Calendar.getInstance(utc)
        c.clear()
        c.set(y, m - 1, d, h, min, 0)
        return c.timeInMillis
    }

    // 2026-11-14 is a Saturday.
    private val saturdayMorning = ms(2026, 11, 14, 10)
    private val dayMs = 86_400_000L

    // ---- Watch Plan ----

    @Test
    fun watchPlan_quickSlotsOfferTonightAndNextSaturday() {
        val slots = WatchPlanLogic.quickSlots(saturdayMorning, utc)
        assertEquals(listOf("In 1 hour", "Tonight 9 PM", "Tomorrow 9 AM", "Tomorrow 9 PM", "Saturday 10 AM"), slots.map { it.label })
        assertEquals(saturdayMorning + 3_600_000L, slots[0].atMs)
        assertEquals(ms(2026, 11, 14, 21), slots[1].atMs)
        assertEquals(ms(2026, 11, 15, 9), slots[2].atMs)
        // It is already Saturday 10:00, so "Saturday 10 AM" means next week.
        assertEquals(ms(2026, 11, 21, 10), slots[4].atMs)
    }

    @Test
    fun watchPlan_tonightSlotIsDroppedWhenTooClose() {
        val slots = WatchPlanLogic.quickSlots(ms(2026, 11, 14, 20, 30), utc)
        assertFalse(slots.any { it.label == "Tonight 9 PM" })
    }

    @Test
    fun watchPlan_statusFollowsTheClock() {
        val due = ms(2026, 11, 14, 21)
        val item = PlannedVideo("a", "t", "c", null, 600, due)
        assertEquals(PlanStatus.Upcoming, WatchPlanLogic.status(item, due - 1))
        assertEquals(PlanStatus.Due, WatchPlanLogic.status(item, due + 10 * 60_000L))
        assertEquals(PlanStatus.Overdue, WatchPlanLogic.status(item, due + 16 * 60_000L))
        assertEquals(PlanStatus.Done, WatchPlanLogic.status(item.copy(done = true), due - 1))
    }

    @Test
    fun watchPlan_sortUpsertAndRoundTrip() {
        val a = PlannedVideo("a", "A", "ch", null, 60, 3_000)
        val b = PlannedVideo("b", "B", "ch", "http://t", 120, 1_000)
        val c = PlannedVideo("c", "C", "ch", null, 90, 2_000, done = true)
        assertEquals(listOf("b", "a", "c"), WatchPlanLogic.sorted(listOf(a, b, c)).map { it.id })

        val replaced = WatchPlanLogic.upsert(listOf(a, b), a.copy(dueAtMs = 9_000))
        assertEquals(2, replaced.size)
        assertEquals(9_000L, replaced.first { it.id == "a" }.dueAtMs)

        val decoded = WatchPlanLogic.deserialize(WatchPlanLogic.serialize(listOf(a, b, c)))
        assertEquals(listOf(a, b, c), decoded)
        assertTrue(WatchPlanLogic.deserialize("not json").isEmpty())
    }

    // ---- Watch Insights ----

    private fun session(id: String, start: Long, seconds: Int, channel: String = "Ch", title: String = "T $id") =
        WatchSession(id, title, channel, start, seconds)

    @Test
    fun insights_resumedVideoMergesAndOldDataIsPruned() {
        val first = session("a", saturdayMorning, 60)
        val resumed = session("a", saturdayMorning + 70_000L, 30)
        val later = session("a", saturdayMorning + 3_600_000L, 20)
        var list = WatchInsightsLogic.addSession(emptyList(), first, saturdayMorning)
        list = WatchInsightsLogic.addSession(list, resumed, saturdayMorning)
        assertEquals(1, list.size)
        assertEquals(90, list[0].seconds)
        list = WatchInsightsLogic.addSession(list, later, saturdayMorning + 3_700_000L)
        assertEquals(2, list.size)
        assertEquals(list, WatchInsightsLogic.addSession(list, session("z", saturdayMorning, 0), saturdayMorning))

        val ancient = session("old", saturdayMorning - 121 * dayMs, 100)
        assertEquals(listOf("a", "a"), WatchInsightsLogic.prune(listOf(ancient) + list, saturdayMorning + 3_700_000L).map { it.videoId })
    }

    @Test
    fun insights_dailyTotalsHoursAndTopChannels() {
        val list = listOf(
            session("a", saturdayMorning, 600, "Alpha"),
            session("b", saturdayMorning - dayMs, 300, "Beta"),
            session("c", ms(2026, 11, 14, 22), 100, "Alpha"),
        )
        assertEquals(listOf(0, 300, 700), WatchInsightsLogic.dailyTotals(list, 3, ms(2026, 11, 14, 23), utc))

        val hours = WatchInsightsLogic.hourHistogram(list, utc)
        // Both the Alpha (600 s) and Beta (300 s) sessions start at 10:00.
        assertEquals(900, hours[10])
        assertEquals(100, hours[22])
        assertEquals(1000, hours.sum())

        val top = WatchInsightsLogic.topChannels(list, 5)
        assertEquals(listOf("Alpha", "Beta"), top.map { it.channel })
        assertEquals(700, top[0].seconds)
        assertEquals(1, WatchInsightsLogic.topChannels(list, 1).size)
    }

    @Test
    fun insights_streakCountsConsecutiveDays() {
        val now = ms(2026, 11, 14, 12)
        assertEquals(0, WatchInsightsLogic.streakDays(emptyList(), now, utc))
        val threeDays = listOf(session("a", now, 60), session("b", now - dayMs, 60), session("c", now - 2 * dayMs, 60))
        assertEquals(3, WatchInsightsLogic.streakDays(threeDays, now, utc))
        // Nothing yet today does not break yesterday's streak.
        assertEquals(2, WatchInsightsLogic.streakDays(threeDays.drop(1), now, utc))
        // A gap ends the streak.
        assertEquals(1, WatchInsightsLogic.streakDays(listOf(session("a", now, 60), session("c", now - 2 * dayMs, 60)), now, utc))
    }

    @Test
    fun insights_searchFormatAndRoundTrip() {
        val list = listOf(
            session("a", saturdayMorning, 120, "Code Club", "Kotlin flows explained"),
            session("b", saturdayMorning + 1000, 60, "Cook Show", "Pasta in ten minutes"),
        )
        val videos = WatchInsightsLogic.videos(list)
        assertEquals(listOf("b", "a"), videos.map { it.videoId })
        assertEquals(listOf("a"), WatchInsightsLogic.search(videos, "KOTLIN  flows").map { it.videoId })
        assertEquals(listOf("b"), WatchInsightsLogic.search(videos, "cook pasta").map { it.videoId })
        assertEquals(2, WatchInsightsLogic.search(videos, "  ").size)
        assertTrue(WatchInsightsLogic.search(videos, "nothing like this").isEmpty())

        assertEquals("<1 min", WatchInsightsLogic.formatDuration(45))
        assertEquals("13 min", WatchInsightsLogic.formatDuration(780))
        assertEquals("1 h 05 min", WatchInsightsLogic.formatDuration(3900))

        assertEquals(list, WatchInsightsLogic.deserialize(WatchInsightsLogic.serialize(list)))
    }

    // ---- Focus Guard ----

    @Test
    fun focus_evaluatePrioritisesTheBudgetOverBreaks() {
        val cfg = FocusConfig(enabled = true, dailyBudgetMin = 60, breakEveryMin = 30)
        assertEquals(FocusAction.None, FocusLogic.evaluate(FocusState(secondsToday = 100, continuousSeconds = 100), cfg.copy(enabled = false)))
        assertEquals(FocusAction.None, FocusLogic.evaluate(FocusState(secondsToday = 100, continuousSeconds = 100), cfg))
        assertEquals(FocusAction.BreakDue(30), FocusLogic.evaluate(FocusState(secondsToday = 1800, continuousSeconds = 1800), cfg))
        assertEquals(FocusAction.None, FocusLogic.evaluate(FocusState(secondsToday = 1800, continuousSeconds = 1800), cfg.copy(breakNudges = false)))
        assertEquals(FocusAction.BudgetReached(60), FocusLogic.evaluate(FocusState(secondsToday = 3600, continuousSeconds = 3600), cfg))
    }

    @Test
    fun focus_advanceHandlesNewDaysAndBreaks() {
        val day = "2026-11-14"
        var s = FocusLogic.advance(FocusState(), day, playing = true, deltaSec = 5)
        s = FocusLogic.advance(s, day, playing = true, deltaSec = 5)
        assertEquals(10, s.secondsToday)
        assertEquals(10, s.continuousSeconds)

        // A pause shorter than two minutes keeps the stretch going.
        val shortPause = FocusLogic.advance(s, day, playing = false, deltaSec = 60)
        assertEquals(10, shortPause.continuousSeconds)
        // A pause of two minutes or more is a break.
        val longPause = FocusLogic.advance(shortPause, day, playing = false, deltaSec = 60)
        assertEquals(0, longPause.continuousSeconds)
        assertEquals(10, longPause.secondsToday)
        // Playing again clears the idle timer.
        assertEquals(0, FocusLogic.advance(longPause, day, playing = true, deltaSec = 5).idleSeconds)

        // A new day starts from zero.
        val next = FocusLogic.advance(s.copy(extraSeconds = 600), "2026-11-15", playing = true, deltaSec = 5)
        assertEquals(5, next.secondsToday)
        assertEquals(0, next.extraSeconds)
    }

    @Test
    fun focus_actionsResetCountersAndGrantGrace() {
        val cfg = FocusConfig(enabled = true, dailyBudgetMin = 10, graceMin = 5)
        val reached = FocusState(secondsToday = 600, continuousSeconds = 600)
        val after = FocusLogic.afterAction(reached, FocusAction.BudgetReached(10), cfg)
        assertEquals(300, after.extraSeconds)
        assertEquals(FocusAction.None, FocusLogic.evaluate(after.copy(continuousSeconds = 0), cfg.copy(breakNudges = false)))
        assertEquals(300, FocusLogic.remainingSeconds(after, cfg))
        assertEquals(0, FocusLogic.afterAction(reached, FocusAction.BreakDue(10), cfg).continuousSeconds)
        assertEquals("2026-11-14", FocusLogic.dayKey(saturdayMorning, utc))
    }

    // ---- Chapter Autopilot ----

    private val chapters = listOf(
        Chapter("Intro", 0L),
        Chapter("Main topic", 30_000L),
        Chapter("Sponsor", 600_000L),
        Chapter("Recap", 650_000L),
        Chapter("Outro", 700_000L),
    )
    private val recapRule = AutopilotRule("recap", AutopilotAction.Speed, speed = 1.5f, enabled = true)

    @Test
    fun autopilot_matchesWholeWordsOnly() {
        assertTrue(ChapterAutopilotLogic.matches("Ad break", "ad"))
        assertTrue(ChapterAutopilotLogic.matches("Sponsor: VPN", "sponsor"))
        assertTrue(ChapterAutopilotLogic.matches("Today's SPONSOR", "sponsor"))
        assertFalse(ChapterAutopilotLogic.matches("Road trip", "ad"))
        assertFalse(ChapterAutopilotLogic.matches("Sponsored by X", "sponsor"))
        assertFalse(ChapterAutopilotLogic.matches("Anything", "  "))
    }

    @Test
    fun autopilot_skipsOnceAndNeverTheLastChapter() {
        val rules = ChapterAutopilotLogic.defaultRules()
        val skip = ChapterAutopilotLogic.decide(chapters, 610_000L, rules, emptySet())
        assertEquals(AutopilotDecision.Skip(650_000L, "Sponsor", 600_000L), skip)
        // Already skipped once: leave it alone so the viewer can rewind.
        assertEquals(AutopilotDecision.None, ChapterAutopilotLogic.decide(chapters, 610_000L, rules, setOf(600_000L)))
        // Intro is off by default.
        assertEquals(AutopilotDecision.None, ChapterAutopilotLogic.decide(chapters, 10_000L, rules, emptySet()))
        // The last chapter is never skipped, even with a matching rule.
        val outroRules = rules + AutopilotRule("outro", AutopilotAction.Skip)
        assertEquals(AutopilotDecision.None, ChapterAutopilotLogic.decide(chapters, 710_000L, outroRules, emptySet()))
        // A single chapter is not enough to act on.
        assertEquals(AutopilotDecision.None, ChapterAutopilotLogic.decide(chapters.take(1), 1_000L, rules, emptySet()))
    }

    @Test
    fun autopilot_speedRulesApplyWhileInsideTheChapter() {
        assertEquals(
            AutopilotDecision.Speed(1.5f, "Recap"),
            ChapterAutopilotLogic.decide(chapters, 660_000L, listOf(recapRule), emptySet()),
        )
        assertEquals(
            AutopilotDecision.None,
            ChapterAutopilotLogic.decide(chapters, 660_000L, listOf(recapRule.copy(enabled = false)), emptySet()),
        )
        assertEquals("Play at 1.5x", ChapterAutopilotLogic.label(recapRule))
        assertEquals("Skip", ChapterAutopilotLogic.label(AutopilotRule("x", AutopilotAction.Skip)))
    }

    @Test
    fun autopilot_addRuleAndRoundTrip() {
        val rules = ChapterAutopilotLogic.defaultRules()
        assertNull(ChapterAutopilotLogic.addRule(rules, AutopilotRule("  ", AutopilotAction.Skip)))
        assertNull(ChapterAutopilotLogic.addRule(rules, AutopilotRule("SPONSOR", AutopilotAction.Skip)))
        val added = ChapterAutopilotLogic.addRule(rules, AutopilotRule(" Merch ", AutopilotAction.Skip))
        assertNotNull(added)
        assertEquals("merch", added!!.last().keyword)

        val config = AutopilotConfig(enabled = true, rules = added)
        val decoded = ChapterAutopilotLogic.deserialize(ChapterAutopilotLogic.serialize(config))
        assertTrue(decoded.enabled)
        assertEquals(config.rules, decoded.rules)
        assertFalse(ChapterAutopilotLogic.deserialize("garbage").enabled)
    }

    // ---- Time-Fit Queue ----

    private fun video(id: String, title: String, seconds: Long, channel: String = "Ch", live: Boolean = false) =
        VideoItem(id, title, channel, null, null, null, seconds, 0L, null, isLive = live)

    @Test
    fun timeFit_neverExceedsTheBudgetAndRanksByInterest() {
        val v1 = video("v1", "Kotlin tips", 900)
        val v2 = video("v2", "Cooking pasta", 600)
        val v3 = video("v3", "Kotlin flows", 600)
        val result = TimeFitLogic.select(listOf(v1, v2, v3), 1200, interest = setOf("kotlin"))
        // v3 (interest, shorter) first; v1 would overshoot so v2 fills the gap.
        assertEquals(listOf("v3", "v2"), result.picks.map { it.id })
        assertEquals(1200L, result.usedSec)
        assertEquals(0L, result.leftSec)
    }

    @Test
    fun timeFit_skipsLiveLongDuplicateAndExcludedVideos() {
        val list = listOf(
            video("live", "Live now", 600, live = true),
            video("long", "Too long", 5000),
            video("tiny", "Clip", 10),
            video("seen", "Already seen", 300),
            video("ok", "Fine", 300),
            video("ok", "Fine again", 300),
        )
        val result = TimeFitLogic.select(list, 1800, exclude = setOf("seen"))
        assertEquals(listOf("ok"), result.picks.map { it.id })
    }

    @Test
    fun timeFit_plannedVideosAndFavouriteChannelsGetABoost() {
        val interesting = video("a", "Kotlin deep dive", 900)
        val planned = video("b", "Something planned", 900)
        val favourite = video("c", "Neutral title", 900, channel = "Fav")
        val first = TimeFitLogic.select(
            listOf(interesting, planned, favourite), 900,
            interest = setOf("kotlin"), favoriteChannels = setOf("fav"), priorityIds = setOf("b"),
        )
        assertEquals(listOf("b"), first.picks.map { it.id })
        val second = TimeFitLogic.select(listOf(favourite, interesting), 900, interest = setOf("kotlin"), favoriteChannels = setOf("fav"))
        assertEquals(listOf("a"), second.picks.map { it.id })
        assertEquals("15 of 15 min · 1 video", TimeFitLogic.summary(first))
    }

    @Test
    fun timeFit_emptyAndImpossibleBudgetsGiveNoPicks() {
        assertTrue(TimeFitLogic.select(emptyList(), 600).picks.isEmpty())
        assertTrue(TimeFitLogic.select(listOf(video("a", "Long", 1200)), 600).picks.isEmpty())
        assertEquals("0 of 10 min · 0 videos", TimeFitLogic.summary(TimeFitLogic.select(emptyList(), 600)))
    }
}
