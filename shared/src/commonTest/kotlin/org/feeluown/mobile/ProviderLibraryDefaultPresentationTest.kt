package org.feeluown.mobile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderLibraryDefaultPresentationTest {
    private val repository = object : ProviderLibraryRepository {}
    private val playlist = ProviderPlaylist(
        id = "playlist:test",
        title = "Test",
        providerId = "test",
        providerName = "Test",
    )
    private val track = MusicTrack(
        id = "track:test",
        title = "Test",
        artists = "Test",
        album = "Test",
        source = "test",
        sourceType = TrackSourceType.Provider,
    )

    @Test
    fun unsupportedMutationDefaultsDoNotExposeDisplayText() = runTest {
        val results = listOf(
            repository.addTrackToPlaylist(playlist, track),
            repository.removeTrackFromPlaylist(playlist, track),
            repository.createPlaylist("test", "Test"),
            repository.deletePlaylist(playlist),
            repository.setSongDisliked(track, true),
            repository.setResourceFavorite("artist", "artist:test", true),
        )

        results.forEach { result ->
            assertFalse(result.success)
            assertTrue(result.message.isBlank())
        }
    }
}
