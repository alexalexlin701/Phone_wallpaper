package com.example.videowallpaper.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.common.util.UnstableApi
import com.example.videowallpaper.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@UnstableApi
@RunWith(AndroidJUnit4::class)
class MainUiDeviceTest {
    @Test fun unifiedHomeHasSingleApplyAction() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                fun children(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
                val buttons = children(root).filterIsInstance<Button>()
                assertEquals(1, buttons.count { it.text.toString() == "Set wallpaper" })
                assertEquals(1, buttons.count { it.text.toString() == "Import video" })
                assertFalse(children(root).any { it is android.widget.Switch })
                val labels = children(root).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
                assertFalse(labels.any { it.contains("Shuffle") || it.contains("Next") || it.contains("Add media") })
                assertEquals(15, children(root).filterIsInstance<android.widget.SeekBar>().single().max)
                val metrics = activity.resources.displayMetrics
                root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                try { root.draw(Canvas(bitmap)); File(activity.cacheDir, "ui-layout.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                finally { bitmap.recycle() }
            }
        }
    }
}
