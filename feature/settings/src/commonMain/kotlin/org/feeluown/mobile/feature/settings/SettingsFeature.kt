package org.feeluown.mobile.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsFeaturePreferences<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT>(
    val themeMode: ThemeModeT,
    val themeColorScheme: ThemeColorSchemeT,
    val themePaletteStyle: ThemePaletteStyleT,
    val themeColorSpec: ThemeColorSpecT,
    val wifiAudioQualityPolicy: AudioQualityT,
    val cellularAudioQualityPolicy: AudioQualityT,
    val unavailablePlaybackPolicy: PlaybackPolicyT,
    val smartReplacementMinScore: Double,
    val pauseOnOtherAppPlayback: Boolean,
    val lyricFontSize: LyricFontSizeT,
    val statusBarLyricsEnabled: Boolean,
    val dynamicCoverColorEnabled: Boolean,
    val downloadParallelism: Int,
    val audioCacheLimitMb: Int,
    val imageCacheLimitMb: Int,
)

data class SettingsFeatureState<PreferencesT, CacheUsageT, DownloadTaskT, LocalMusicStateT>(
    val preferences: PreferencesT,
    val cacheUsage: CacheUsageT,
    val downloadTasks: List<DownloadTaskT>,
    val localMusic: LocalMusicStateT,
    val statusBarLyricsAvailable: Boolean = false,
    val debugLogViewerAvailable: Boolean = false,
    val isBusy: Boolean = false,
    val feedback: String? = null,
)

interface SettingsPreferencesPort<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT> {
    val state: StateFlow<SettingsFeaturePreferences<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT>>

    suspend fun awaitPreferences(): SettingsFeaturePreferences<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT>
    suspend fun setThemeMode(value: ThemeModeT)
    suspend fun setThemeColorScheme(value: ThemeColorSchemeT)
    suspend fun setThemePaletteStyle(value: ThemePaletteStyleT)
    suspend fun setThemeColorSpec(value: ThemeColorSpecT)
    suspend fun setWifiAudioQualityPolicy(value: AudioQualityT)
    suspend fun setCellularAudioQualityPolicy(value: AudioQualityT)
    suspend fun setUnavailablePlaybackPolicy(value: PlaybackPolicyT)
    suspend fun setSmartReplacementMinScore(value: Double)
    suspend fun setPauseOnOtherAppPlayback(value: Boolean)
    suspend fun setLyricFontSize(value: LyricFontSizeT)
    suspend fun setStatusBarLyricsEnabled(value: Boolean)
    suspend fun setDynamicCoverColorEnabled(value: Boolean)
    suspend fun setDownloadParallelism(value: Int)
    suspend fun setCacheLimits(audioMb: Int, imageMb: Int)
}

fun interface SettingsAudioQualityPort<AudioQualityT> {
    suspend fun apply(wifi: AudioQualityT, cellular: AudioQualityT)
}

interface SettingsDownloadPort<DownloadTaskT> {
    val tasks: StateFlow<List<DownloadTaskT>>
    suspend fun updateParallelism(value: Int)
}

interface SettingsCachePort<CacheUsageT> {
    val usage: StateFlow<CacheUsageT>
    suspend fun updateLimit(audioMaxBytes: Long, imageMaxBytes: Long)
    suspend fun clearAll()
    suspend fun refreshUsage()
}

interface SettingsLocalMusicPort<LocalMusicStateT> {
    val state: StateFlow<LocalMusicStateT>
    fun refreshDirectories()
    fun setDirectoryEnabled(directoryId: String, enabled: Boolean)
    fun setMinDurationSeconds(value: Int)
}

interface SettingsNavigationPort {
    fun close()
    fun openDownloadManager()
    fun openDebugLogs()
}

interface SettingsFeatureOwner<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT, CacheUsageT, DownloadTaskT, LocalMusicStateT> {
    val state: StateFlow<SettingsFeatureState<SettingsFeaturePreferences<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT>, CacheUsageT, DownloadTaskT, LocalMusicStateT>>

    fun close()
    fun setThemeMode(value: ThemeModeT)
    fun setThemeColorScheme(value: ThemeColorSchemeT)
    fun setThemePaletteStyle(value: ThemePaletteStyleT)
    fun setThemeColorSpec(value: ThemeColorSpecT)
    fun setWifiAudioQualityPolicy(value: AudioQualityT)
    fun setCellularAudioQualityPolicy(value: AudioQualityT)
    fun setUnavailablePlaybackPolicy(value: PlaybackPolicyT)
    fun setSmartReplacementMinScore(value: Double)
    fun setPauseOnOtherAppPlayback(value: Boolean)
    fun setLyricFontSize(value: LyricFontSizeT)
    fun setStatusBarLyricsEnabled(value: Boolean)
    fun setDynamicCoverColorEnabled(value: Boolean)
    fun setDownloadParallelism(value: Int)
    fun setAudioCacheLimitMb(value: Int)
    fun setImageCacheLimitMb(value: Int)
    fun refreshLocalMusicDirectories()
    fun setLocalMusicDirectoryEnabled(directoryId: String, enabled: Boolean)
    fun setLocalMusicMinDurationSeconds(value: Int)
    fun clearCache()
    fun refreshCacheUsage()
    fun openDownloadManager()
    fun openDebugLogs()
    fun setStatusBarLyricsAvailability(available: Boolean)
    fun dismissFeedback(feedback: String)
}

fun <ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT, CacheUsageT, DownloadTaskT, LocalMusicStateT> createSettingsFeatureOwner(
    preferences: SettingsPreferencesPort<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT>,
    audioQuality: SettingsAudioQualityPort<AudioQualityT>,
    downloads: SettingsDownloadPort<DownloadTaskT>,
    cache: SettingsCachePort<CacheUsageT>,
    localMusic: SettingsLocalMusicPort<LocalMusicStateT>,
    navigation: SettingsNavigationPort,
    debugLogViewerAvailable: Boolean,
    scope: CoroutineScope,
): SettingsFeatureOwner<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT, CacheUsageT, DownloadTaskT, LocalMusicStateT> = DefaultSettingsFeatureOwner(
    preferences = preferences,
    audioQuality = audioQuality,
    downloads = downloads,
    cache = cache,
    localMusic = localMusic,
    navigation = navigation,
    debugLogViewerAvailable = debugLogViewerAvailable,
    scope = scope,
)

private class DefaultSettingsFeatureOwner<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT, CacheUsageT, DownloadTaskT, LocalMusicStateT>(
    private val preferences: SettingsPreferencesPort<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT>,
    private val audioQuality: SettingsAudioQualityPort<AudioQualityT>,
    private val downloads: SettingsDownloadPort<DownloadTaskT>,
    private val cache: SettingsCachePort<CacheUsageT>,
    private val localMusic: SettingsLocalMusicPort<LocalMusicStateT>,
    private val navigation: SettingsNavigationPort,
    debugLogViewerAvailable: Boolean,
    private val scope: CoroutineScope,
) : SettingsFeatureOwner<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT, CacheUsageT, DownloadTaskT, LocalMusicStateT> {
    private val mutableState = MutableStateFlow(
        SettingsFeatureState(
            preferences = preferences.state.value,
            cacheUsage = cache.usage.value,
            downloadTasks = downloads.tasks.value,
            localMusic = localMusic.state.value,
            debugLogViewerAvailable = debugLogViewerAvailable,
        )
    )
    override val state: StateFlow<SettingsFeatureState<SettingsFeaturePreferences<ThemeModeT, ThemeColorSchemeT, ThemePaletteStyleT, ThemeColorSpecT, AudioQualityT, PlaybackPolicyT, LyricFontSizeT>, CacheUsageT, DownloadTaskT, LocalMusicStateT>> = mutableState.asStateFlow()

    init {
        scope.launch {
            combine(
                preferences.state,
                cache.usage,
                downloads.tasks,
                localMusic.state,
            ) { currentPreferences, cacheUsage, downloadTasks, localMusicState ->
                mutableState.value.copy(
                    preferences = currentPreferences,
                    cacheUsage = cacheUsage,
                    downloadTasks = downloadTasks,
                    localMusic = localMusicState,
                )
            }.collect { mutableState.value = it }
        }
        scope.launch {
            runCatching {
                val current = preferences.awaitPreferences()
                audioQuality.apply(current.wifiAudioQualityPolicy, current.cellularAudioQualityPolicy)
            }.onFailure(::failed)
        }
        scope.launch {
            runCatching {
                val current = preferences.awaitPreferences()
                cache.updateLimit(
                    audioMaxBytes = mbToBytes(current.audioCacheLimitMb),
                    imageMaxBytes = mbToBytes(current.imageCacheLimitMb),
                )
                cache.refreshUsage()
            }.onFailure(::failed)
        }
    }

    override fun close() = navigation.close()

    override fun setThemeMode(value: ThemeModeT) = launchPreferenceUpdate { preferences.setThemeMode(value) }
    override fun setThemeColorScheme(value: ThemeColorSchemeT) = launchPreferenceUpdate { preferences.setThemeColorScheme(value) }
    override fun setThemePaletteStyle(value: ThemePaletteStyleT) = launchPreferenceUpdate { preferences.setThemePaletteStyle(value) }
    override fun setThemeColorSpec(value: ThemeColorSpecT) = launchPreferenceUpdate { preferences.setThemeColorSpec(value) }
    override fun setUnavailablePlaybackPolicy(value: PlaybackPolicyT) = launchPreferenceUpdate { preferences.setUnavailablePlaybackPolicy(value) }
    override fun setSmartReplacementMinScore(value: Double) = launchPreferenceUpdate {
        preferences.setSmartReplacementMinScore(value.coerceIn(0.0, 1.0))
    }
    override fun setPauseOnOtherAppPlayback(value: Boolean) = launchPreferenceUpdate { preferences.setPauseOnOtherAppPlayback(value) }
    override fun setLyricFontSize(value: LyricFontSizeT) = launchPreferenceUpdate { preferences.setLyricFontSize(value) }
    override fun setStatusBarLyricsEnabled(value: Boolean) = launchPreferenceUpdate { preferences.setStatusBarLyricsEnabled(value) }
    override fun setDynamicCoverColorEnabled(value: Boolean) = launchPreferenceUpdate { preferences.setDynamicCoverColorEnabled(value) }

    override fun setWifiAudioQualityPolicy(value: AudioQualityT) {
        scope.launch {
            runCatching {
                preferences.setWifiAudioQualityPolicy(value)
                val current = preferences.state.value
                audioQuality.apply(value, current.cellularAudioQualityPolicy)
            }.onFailure(::failed)
        }
    }

    override fun setCellularAudioQualityPolicy(value: AudioQualityT) {
        scope.launch {
            runCatching {
                preferences.setCellularAudioQualityPolicy(value)
                val current = preferences.state.value
                audioQuality.apply(current.wifiAudioQualityPolicy, value)
            }.onFailure(::failed)
        }
    }

    override fun setDownloadParallelism(value: Int) {
        val normalized = value.coerceIn(1, 5)
        scope.launch {
            runCatching {
                preferences.setDownloadParallelism(normalized)
                downloads.updateParallelism(normalized)
            }.onFailure(::failed)
        }
    }

    override fun setAudioCacheLimitMb(value: Int) {
        updateCacheLimits(audioMb = value.coerceAtLeast(0), imageMb = null)
    }

    override fun setImageCacheLimitMb(value: Int) {
        updateCacheLimits(audioMb = null, imageMb = value.coerceAtLeast(0))
    }

    override fun refreshLocalMusicDirectories() = localMusic.refreshDirectories()
    override fun setLocalMusicDirectoryEnabled(directoryId: String, enabled: Boolean) = localMusic.setDirectoryEnabled(directoryId, enabled)
    override fun setLocalMusicMinDurationSeconds(value: Int) = localMusic.setMinDurationSeconds(value)

    override fun clearCache() {
        scope.launch {
            busy("正在清理缓存")
            runCatching {
                cache.clearAll()
                cache.refreshUsage()
            }.onSuccess {
                done("缓存已清理")
            }.onFailure(::failed)
        }
    }

    override fun refreshCacheUsage() {
        scope.launch { runCatching { cache.refreshUsage() }.onFailure(::failed) }
    }

    override fun openDownloadManager() = navigation.openDownloadManager()

    override fun openDebugLogs() {
        if (state.value.debugLogViewerAvailable) navigation.openDebugLogs()
    }

    override fun setStatusBarLyricsAvailability(available: Boolean) {
        mutableState.value = mutableState.value.copy(statusBarLyricsAvailable = available)
    }

    override fun dismissFeedback(feedback: String) {
        if (state.value.feedback == feedback) mutableState.value = state.value.copy(feedback = null)
    }

    private fun launchPreferenceUpdate(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure(::failed) }
    }

    private fun updateCacheLimits(audioMb: Int?, imageMb: Int?) {
        scope.launch {
            runCatching {
                val current = preferences.state.value
                val nextAudio = audioMb ?: current.audioCacheLimitMb
                val nextImage = imageMb ?: current.imageCacheLimitMb
                preferences.setCacheLimits(nextAudio, nextImage)
                cache.updateLimit(mbToBytes(nextAudio), mbToBytes(nextImage))
                cache.refreshUsage()
            }.onFailure(::failed)
        }
    }

    private fun busy(message: String) {
        mutableState.value = mutableState.value.copy(isBusy = true, feedback = message)
    }

    private fun done(message: String) {
        mutableState.value = mutableState.value.copy(isBusy = false, feedback = message)
    }

    private fun failed(throwable: Throwable) {
        mutableState.value = mutableState.value.copy(
            isBusy = false,
            feedback = throwable.message ?: throwable::class.simpleName.orEmpty().ifBlank { "操作失败" },
        )
    }
}

fun mbToBytes(value: Int): Long = value.coerceAtLeast(0).toLong() * 1024L * 1024L
