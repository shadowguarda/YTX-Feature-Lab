package com.tensei.ytxtube

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.tensei.ytxtube.potoken.PoTokenGenerator
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class YTXTubeApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        TvMode.init(this)
        GlassQuality.init(this)
        GlassSettings.init(this)
        AmbientSettings.init(this)
        DeArrow.init(this)
        ContentFilters.init(this)
        Tweaks.init(this)
        Watched.init(this)
        UsageStats.init(this)
        SettingsMemory.init(this)
        DownloadManagerAdvanced.init(this)
        GestureDesigner.init(this)
        HomeFeedSettings.init(this)
        LiquidGlassThemeEngine.init(this)
        MiniPlayerSystem.init(this)
        MusicExperience.init(this)
        NavigationSettings.init(this)
        UiVisibilityManager.init(this)
        PlayerEnhancements.init(this)
        PremiumFeatures.init(this)
        PrivacyPersonalisation.init(this)
        AiIntelligenceSettings.init(this)
        AutomationEngine.init(this)
        PrivacySecurityCenter.init(this)
        LiquidGlassStudio.init(this)
        PlayerLayoutManager.init(this)
        ScopedThemeManager.init(this)
        OfflineCollectionsManager.init(this)
        QueueDragAndPlaylistManager.init(this)
        MusicFoldersManager.init(this)
        SubscriptionFoldersManager.init(this)
        HomeDashboardManager.init(this)
        OfflineIntelligence.init(this)
        PerformanceAndExperimentalEngine.init(this)
        ProfessionalMusicSystem.init(this)
        DiscoveryAndSubsManager.init(this)
        VideoColorEngine.init(this)
        VideoExperience.init(this)
        Discovery.init(this)
        ListenBrainz.init(this)
        QueueStore.init(this)
        SkipSilence.init(this)
        AudioEffects.init(this)
        NetworkSpeed.init(this)
        PlaybackBandwidth.init(this)
        NewPipe.init(NewPipeDownloader.instance)


        val poTokenGenerator = PoTokenGenerator()
        poTokenGenerator.initialize()
        YoutubeStreamExtractor.setPoTokenProvider(YtxPoTokenProvider(poTokenGenerator))


        YoutubeStreamExtractor.setFetchIosClient(true)
        HdrItags.install()
        ThemeSettings.init(this)
        HdrSupport.init(this)
        QualityPreference.init(this)
        PlaybackSpeed.init(this)
        AudioOnly.init(this)
        AudioTrackPreference.init(this)
        CaptionPreference.init(this)
        VideoGestureSettings.init(this)
        ResumeStore.init(this)
        Account.init(this)
        SponsorBlock.init(this)
        ReturnDislike.init(this)
        SearchHistory.init(this)
        Downloads.init(this)

        getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                private var last: android.net.Network? = null
                override fun onAvailable(network: android.net.Network) {
                    if (last != null && last != network) StreamCache.invalidateAll()
                    last = network
                }
            },
        )
    }


    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(180)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .build()
}
