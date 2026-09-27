package com.example.videowallpaper.media

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videowallpaper.MainActivity
import com.example.videowallpaper.data.MediaLibrary
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PreviewPowerDeviceTest {
    @Test fun leavingResumedStateReleasesDecoderAndReturningRestoresIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val library = MediaLibrary(instrumentation.targetContext)
        val source = library.entries().firstOrNull()?.let { library.playback(it) }
        assumeNotNull(source)
        fun video(view: View): PlayerView? {
            if (view is PlayerView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) video(view.getChildAt(i))?.let { return it }
            return null
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertNotNull(video(it.findViewById(android.R.id.content))!!.player) }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { assertNull(video(it.findViewById(android.R.id.content))!!.player) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertNotNull(video(it.findViewById(android.R.id.content))!!.player) }
        }
    }
}
