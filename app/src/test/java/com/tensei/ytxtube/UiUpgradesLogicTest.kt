package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiUpgradesLogicTest {
    private val tabs = listOf("Home", "Music", "Shorts", "Library", "Downloads")

    @Test
    fun layoutTogglesRespectLockAndMinimum() {
        var list = OrderedLayoutLogic.defaults(tabs)
        // Home is locked.
        assertEquals(list, OrderedLayoutLogic.toggle(list, "Home", minVisible = 2, locked = "Home"))
        list = OrderedLayoutLogic.toggle(list, "Shorts", 2, "Home")
        assertEquals(listOf("Home", "Music", "Library", "Downloads"), OrderedLayoutLogic.visibleNames(list))
        list = OrderedLayoutLogic.toggle(list, "Music", 2, "Home")
        list = OrderedLayoutLogic.toggle(list, "Library", 2, "Home")
        // Home and Downloads remain: the last two cannot be hidden.
        assertEquals(listOf("Home", "Downloads"), OrderedLayoutLogic.visibleNames(list))
        assertEquals(list, OrderedLayoutLogic.toggle(list, "Downloads", 2, "Home"))
        // A hidden one can come back.
        assertEquals(3, OrderedLayoutLogic.visibleNames(OrderedLayoutLogic.toggle(list, "Music", 2, "Home")).size)
        assertEquals(list, OrderedLayoutLogic.toggle(list, "Nope", 2, "Home"))
    }

    @Test
    fun layoutMovesStayInsideTheList() {
        val list = OrderedLayoutLogic.defaults(listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), OrderedLayoutLogic.move(list, "a", 1).map { it.name })
        assertEquals(listOf("a", "c", "b"), OrderedLayoutLogic.move(list, "c", -1).map { it.name })
        assertEquals(list, OrderedLayoutLogic.move(list, "a", -1))
        assertEquals(list, OrderedLayoutLogic.move(list, "c", 1))
        assertEquals(list, OrderedLayoutLogic.move(list, "zzz", 1))
    }

    @Test
    fun layoutSavesAndRepairsItself() {
        val saved = OrderedLayoutLogic.serialize(
            listOf(LayoutItem("Music", true), LayoutItem("Home", true), LayoutItem("Shorts", false)),
        )
        assertEquals("Music:1,Home:1,Shorts:0", saved)
        // Unknown names dropped, missing ones appended visible.
        val loaded = OrderedLayoutLogic.deserialize("Ghost:1,$saved", tabs, 2, "Home")
        assertEquals(listOf("Music", "Home", "Shorts", "Library", "Downloads"), loaded.map { it.name })
        assertEquals(listOf("Music", "Home", "Library", "Downloads"), OrderedLayoutLogic.visibleNames(loaded))
        // Empty or damaged data gives the defaults.
        assertEquals(OrderedLayoutLogic.defaults(tabs), OrderedLayoutLogic.deserialize(null, tabs))
        assertEquals(OrderedLayoutLogic.defaults(tabs), OrderedLayoutLogic.deserialize("", tabs))
        // Hiding Home, or too many tabs, resets everything to visible.
        assertTrue(OrderedLayoutLogic.deserialize("Home:0,Music:1", tabs, 2, "Home").all { it.visible })
        assertTrue(OrderedLayoutLogic.deserialize("Home:1,Music:0,Shorts:0,Library:0,Downloads:0", tabs, 2, "Home").all { it.visible })
    }

    @Test
    fun dockLabelsFollowTheMode() {
        assertEquals(true, NavDockLogic.showLabel(DockLabelMode.Always, selected = false, iconOnly = false))
        assertEquals(false, NavDockLogic.showLabel(DockLabelMode.SelectedOnly, selected = false, iconOnly = false))
        assertEquals(true, NavDockLogic.showLabel(DockLabelMode.SelectedOnly, selected = true, iconOnly = false))
        assertEquals(false, NavDockLogic.showLabel(DockLabelMode.Never, selected = true, iconOnly = false))
        assertEquals(false, NavDockLogic.showLabel(DockLabelMode.Always, selected = true, iconOnly = true))
        assertEquals(DockLabelMode.SelectedOnly, NavDockLogic.parseLabelMode("SelectedOnly"))
        assertEquals(DockLabelMode.Always, NavDockLogic.parseLabelMode("nonsense"))
    }

    @Test
    fun continueWatchingKeepsStartedUnfinishedVideosNewestFirst() {
        fun s(id: String) = LoggedVideo(id, "T $id", "C", 10, 0L, 1)
        val progress = mapOf("a" to 0.5f, "b" to 0.01f, "c" to 0.97f, "d" to 0.2f)
        val result = HomeLayoutLogic.continueWatching(listOf(s("a"), s("b"), s("c"), s("d"), s("e")), { progress[it] })
        assertEquals(listOf("a", "d"), result.map { it.videoId })
        assertEquals(1, HomeLayoutLogic.continueWatching(listOf(s("a"), s("d")), { progress[it] }, max = 1).size)
    }

    @Test
    fun islandProgressIsClamped() {
        assertEquals(0f, IslandLogic.fraction(500, 0), 0f)
        assertEquals(0.25f, IslandLogic.fraction(250, 1000), 0.0001f)
        assertEquals(1f, IslandLogic.fraction(5000, 1000), 0f)
        assertEquals(0f, IslandLogic.fraction(-5, 1000), 0f)
    }

    private fun px(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun artworkColourPicksTheVividShadeNotTheGrey() {
        val red = px(200, 30, 40)
        val pixels = IntArray(100) { if (it < 30) red else px(128, 128, 128) } // 70 grey, 30 red
        val picked = ArtworkColorLogic.dominant(pixels, px(1, 2, 3))
        assertEquals(255, picked ushr 24)
        assertTrue(((picked shr 16) and 0xFF) > 180)
        assertTrue((picked and 0xFF) < 80)
    }

    @Test
    fun artworkColourFallbacks() {
        val fallback = px(10, 20, 30)
        assertEquals(fallback, ArtworkColorLogic.dominant(IntArray(0), fallback))
        assertEquals(fallback, ArtworkColorLogic.dominant(intArrayOf(px(255, 0, 0, a = 0)), fallback))
        // All grey: the plain average is used.
        assertEquals(px(100, 100, 100), ArtworkColorLogic.dominant(intArrayOf(px(100, 100, 100), px(100, 100, 100)), fallback))
        assertEquals(px(150, 100, 50), ArtworkColorLogic.average(intArrayOf(px(100, 100, 100), px(200, 100, 0)), fallback))
        // Black is skipped by the vivid pick but still averaged.
        assertEquals(px(0, 0, 0), ArtworkColorLogic.dominant(intArrayOf(px(0, 0, 0)), fallback))
    }
}
