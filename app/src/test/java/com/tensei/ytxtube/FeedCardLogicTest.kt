package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedCardLogicTest {
    @Test fun rowsChunkWithShortLastRow() {
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), FeedCardLogic.rows(listOf(1, 2, 3, 4, 5), 2))
        assertEquals(listOf(listOf(1), listOf(2)), FeedCardLogic.rows(listOf(1, 2), 0))
        assertEquals(emptyList<List<Int>>(), FeedCardLogic.rows(emptyList<Int>(), 2))
    }

    @Test fun parseFallsBackToCards() {
        assertEquals(FeedCardStyle.Grid, FeedCardLogic.parse("Grid"))
        assertEquals(FeedCardStyle.Comfortable, FeedCardLogic.parse("nope"))
        assertEquals(FeedCardStyle.Comfortable, FeedCardLogic.parse(null))
        assertEquals(2, FeedCardStyle.Grid.columns)
    }
}

class HomePresetLogicTest {
    @Test fun everyPresetListsEveryShelfOnce() {
        for (p in HomePreset.entries) {
            assertEquals(HomeLayoutLogic.ALL.toSet(), HomePresetLogic.shelves(p).map { it.name }.toSet())
            assertEquals(HomeLayoutLogic.ALL.size, HomePresetLogic.shelves(p).size)
        }
    }

    @Test fun minimalHidesAllShelvesAndFocusKeepsOnlyContinue() {
        assertEquals(0, HomePresetLogic.shelves(HomePreset.Minimal).count { it.visible })
        assertEquals(listOf(HomeLayoutLogic.CONTINUE), OrderedLayoutLogic.visibleNames(HomePresetLogic.shelves(HomePreset.Focus)))
        assertEquals(HomeLayoutLogic.SUBSCRIPTIONS, HomePresetLogic.shelves(HomePreset.Browse).first().name)
    }

    @Test fun matchingFindsAppliedPresetAndNothingForCustom() {
        for (p in HomePreset.entries) {
            assertEquals(p, HomePresetLogic.matching(HomePresetLogic.cardStyle(p), HomePresetLogic.labelMode(p), HomePresetLogic.shelves(p)))
        }
        assertEquals(null, HomePresetLogic.matching(FeedCardStyle.Grid, DockLabelMode.Never, HomePresetLogic.shelves(HomePreset.Classic)))
    }
}
