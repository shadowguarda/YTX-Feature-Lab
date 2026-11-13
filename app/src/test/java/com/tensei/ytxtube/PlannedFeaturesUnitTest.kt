package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedFeaturesUnitTest {

    @Test
    fun playerLayout_moveSlotWithinZone_andRoundTrip() {
        val initial = PlayerLayoutLogic.defaultSlots(ControlLayoutPreset.Standard)
        val moved = PlayerLayoutLogic.moveSlotWithinZone(initial, PlayerControlAction.SETTINGS, -1)
        val topBar = moved.filter { it.zone == PlayerControlZone.TOP_BAR }.sortedBy { it.order }
        assertEquals(PlayerControlAction.SETTINGS, topBar[2].action)

        val state = PlayerLayoutState(
            activePreset = ControlLayoutPreset.Studio,
            slots = moved,
            compactTopBar = true,
            showTimeRemaining = true,
        )
        val decoded = PlayerLayoutLogic.deserialize(PlayerLayoutLogic.serialize(state))
        assertEquals(ControlLayoutPreset.Studio, decoded.activePreset)
        assertTrue(decoded.compactTopBar)
        assertEquals(moved.size, decoded.slots.size)
    }

    @Test
    fun themeBundleCodec_exportImport_andRejectInvalidHex() {
        val state = ScopedThemeState(
            separatePlayerTheme = true,
            homeTheme = ScopedThemeConfig(accentHex = "#FF4081", glassOpacity = 0.8f),
            playerTheme = ScopedThemeConfig(accentHex = "#00E5FF", glassOpacity = 0.6f),
        )
        val json = ThemeBundleCodec.exportJson(state, "Neon Night")
        val result = ThemeBundleCodec.importJson(json)
        assertTrue(result is ThemeImportResult.Success)
        val imported = (result as ThemeImportResult.Success).state
        assertTrue(imported.separatePlayerTheme)
        assertEquals("#FF4081", imported.homeTheme.accentHex)
        assertEquals("#00E5FF", imported.playerTheme.accentHex)

        val invalid = ThemeBundleCodec.importJson("""{"schemaVersion":1,"homeTheme":{"accentHex":"NOT_HEX"}}""")
        assertTrue(invalid is ThemeImportResult.Error)
    }

    @Test
    fun offlineCollections_reorderAndSerialize() {
        val ids = listOf("v1", "v2", "v3")
        val reordered = OfflineCollectionsLogic.moveId(ids, 2, 0)
        assertEquals(listOf("v3", "v1", "v2"), reordered)

        val state = OfflineCollectionsState(
            collections = listOf(OfflineCollection("c1", "Travel Mix", listOf("v1", "v3"))),
            orderedQueueIds = reordered,
            activeProfileId = "wifi_only",
        )
        val decoded = OfflineCollectionsLogic.deserialize(OfflineCollectionsLogic.serialize(state))
        assertEquals("wifi_only", decoded.activeProfileId)
        assertEquals(listOf("v3", "v1", "v2"), decoded.orderedQueueIds)
        assertEquals("Travel Mix", decoded.collections.first().name)
    }

    @Test
    fun queueReorder_moveAndPlaylistSerialization() {
        val v1 = VideoItem("a", "Track A", "Ch A", null, null, null, 120, 100, null)
        val v2 = VideoItem("b", "Track B", "Ch B", null, null, null, 180, 200, null)
        val moved = QueueReorderLogic.moveItem(listOf(v1, v2), 0, 1)
        assertEquals(listOf("b", "a"), moved.map { it.id })

        val playlists = listOf(LocalSavedPlaylist("p1", "Focus", moved, 1000L))
        val decoded = QueueReorderLogic.deserializePlaylists(QueueReorderLogic.serializePlaylists(playlists))
        assertEquals(1, decoded.size)
        assertEquals("Focus", decoded.first().name)
        assertEquals(listOf("b", "a"), decoded.first().items.map { it.id })
    }

    @Test
    fun musicCrossfadeMath_equalPowerAndLinearBounds() {
        val (outStart, inStart) = MusicCrossfadeMath.computeGains(0f, CrossfadeCurve.EQUAL_POWER)
        assertEquals(1f, outStart, 0.001f)
        assertEquals(0f, inStart, 0.001f)

        val (outMid, inMid) = MusicCrossfadeMath.computeGains(0.5f, CrossfadeCurve.EQUAL_POWER)
        assertEquals(outMid, inMid, 0.001f)
        assertTrue(outMid > 0.7f)

        val (outEnd, inEnd) = MusicCrossfadeMath.computeGains(1f, CrossfadeCurve.LINEAR)
        assertEquals(0f, outEnd, 0.001f)
        assertEquals(1f, inEnd, 0.001f)
    }

    @Test
    fun subscriptionOpmlCodec_exportAndChannelExtraction() {
        val channels = listOf(
            ChannelRef("UC123&45", "Tech & Code", null),
            ChannelRef("UC999", "Music Lab", null),
        )
        val folders = listOf(ChannelFolder("f1", "Coding", listOf("UC123&45")))
        val xml = SubscriptionOpmlCodec.exportOpml(channels, folders)
        assertTrue(xml.contains("Tech &amp; Code"))
        assertTrue(xml.contains("UC123&amp;45"))
        assertEquals("UC999", SubscriptionOpmlCodec.extractChannelIdFromFeedUrl("https://www.youtube.com/feeds/videos.xml?channel_id=UC999"))
    }

    @Test
    fun localHomeFeedEngine_discoversUnsubscribedAndMixesFeed() {
        val subVideo = VideoItem("s1", "Kotlin Compose Tips", "SubChannel", null, null, null, 300, 500, null)
        val newVideo = VideoItem("d1", "Kotlin Flows Deep Dive", "NewCreator", null, null, null, 400, 900, null)

        val keywords = LocalHomeFeedEngine.extractInterestKeywords(listOf("Kotlin Compose Tutorial"))
        assertTrue("kotlin" in keywords)

        val discovered = LocalHomeFeedEngine.discoverNewChannels(
            candidates = listOf(subVideo, newVideo),
            subscribedChannelNames = setOf("SubChannel"),
            watchedChannelNames = emptySet(),
            interestKeywords = keywords,
        )
        assertEquals(1, discovered.size)
        assertEquals("NewCreator", discovered.first().channelName)

        val mixed = LocalHomeFeedEngine.buildLocalHomeFeed(
            subscriptionVideos = listOf(subVideo),
            candidateVideos = listOf(newVideo),
            watchedVideoIds = emptySet(),
            interestKeywords = keywords,
        )
        assertEquals(listOf("s1", "d1"), mixed.map { it.id })
    }

    @Test
    fun homeDashboardLogic_pinAndReorderPersistence() {
        val defaults = HomeDashboardLogic.defaultSections()
        val withPinned = defaults.map {
            if (it.section == HomeSection.WatchLater) it.copy(pinned = true) else it
        }
        val normalized = HomeDashboardLogic.normalizePinnedOrder(withPinned)
        assertEquals(HomeSection.WatchLater, normalized.first().section)

        val roundTrip = HomeDashboardLogic.deserialize(HomeDashboardLogic.serialize(normalized))
        assertEquals(HomeSection.WatchLater, roundTrip.first().section)
        assertFalse(roundTrip.last().pinned)
    }
}