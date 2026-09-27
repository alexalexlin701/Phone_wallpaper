package com.example.videowallpaper.media

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videowallpaper.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLibraryDeviceTest {
    @Test fun singleVideoUpgradeStopsRotationAndPreservesAppliedWallpaper() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_single_video_upgrade"
        context.deleteSharedPreferences(name)
        try {
            val store = MediaLibrary(context, name, false)
            val photo = MediaEntry("content://test/photo", "photo", false)
            val first = MediaEntry("content://test/first", "first", true)
            val second = MediaEntry("content://test/second", "second", true)
            store.setEntries(listOf(photo, first, second))
            store.rotate = true
            store.gamma = 3.5f
            store.stage(2)
            store.commitPending()
            store.useSingleVideo()
            assertEquals(listOf(first), store.entries())
            assertFalse(store.rotate)
            assertFalse(store.activeRotate())
            assertEquals(second, store.current())
            assertEquals(3.5f, store.gamma)
            store.setEntries(listOf(second))
            assertEquals(listOf(second), store.entries())
            store.useSingleVideo()
            assertEquals(listOf(second), store.entries())
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun draftAndPreviewDoNotChangeAppliedPlaylistAndMixedRotationAvoidsRepeat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_media_library"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val store = MediaLibrary(context, name, false)
            val photo = MediaEntry("content://test/photo", "photo", false)
            val video = MediaEntry("content://test/video", "video", true)
            store.setEntries(listOf(photo))
            store.target = 2
            store.stage(0)
            assertNull(store.current())
            assertEquals(photo, store.current(true))
            store.commitPending()
            assertEquals(2, store.activeTarget())
            assertFalse(store.isLive())
            store.setEntries(listOf(photo, video))
            store.pingPong = true
            store.target = 3
            store.stage(1)
            assertEquals(photo, store.current())
            assertFalse(store.isLive())
            assertEquals(video, store.current(true))
            assertNull(store.playback(video))
            assertTrue(MediaLibrary(context, name, false).pingPong)
            store.pingPong = false
            store.stage(1)
            store.commitPending()
            assertTrue(store.isLive())
            assertEquals(3, store.activeTarget())
            assertEquals(video, store.current())
            val next = store.nextChoice()!!
            assertEquals(photo.uri, next.uri)
            store.advance(next)
            assertEquals(photo, store.current())
            assertEquals(video.uri, store.nextChoice()!!.uri)
            store.setEntries(emptyList())
            assertEquals(2, store.entries(true).size)
        } finally { context.deleteSharedPreferences(name) }
    }
}
