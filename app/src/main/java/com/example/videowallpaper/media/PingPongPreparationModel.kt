package com.example.videowallpaper.media

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.example.videowallpaper.data.MediaLibrary
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PingPongPreparationModel(application: Application) : AndroidViewModel(application) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    var onChanged: (() -> Unit)? = null
    var busy = false; private set
    var percent = 0; private set
    var detail = ""; private set
    val isCancelling get() = busy && cancelled.get()
    fun prepare() {
        if (busy) return
        val app = getApplication<Application>()
        val library = MediaLibrary(app)
        val gamma = library.gamma
        val pending = library.entries().filter { it.video && library.prepared(it.uri) == null }
        if (pending.isEmpty()) { detail = "Video is ready."; onChanged?.invoke(); return }
        busy = true; percent = 0; detail = "Preparing video..."; cancelled.set(false); onChanged?.invoke()
        worker.execute {
            var result = "Video is ready."
            try {
                val directory = File(app.filesDir, "pingpong").apply { mkdirs() }
                directory.listFiles()?.filter { it.name.endsWith(".frames") }?.forEach { it.delete() }
                pending.forEachIndexed { index, entry ->
                    val output = File(directory, "pingpong-${UUID.randomUUID()}.mp4")
                    try {
                        PingPongEncoder().render(app, android.net.Uri.parse(entry.uri), output, cancelled::get, gamma, { value ->
                            main.post { percent = (index * 100 + value) / pending.size; detail = "Preparing video: $percent%"; onChanged?.invoke() }
                        })
                        if (cancelled.get()) throw CancellationException()
                        library.cache(entry.uri, output, gamma)
                    } catch (e: Throwable) { output.delete(); throw e }
                }
            } catch (_: CancellationException) { result = "Processing cancelled. Tap Prepare video to retry." }
            catch (e: Exception) { result = "Processing failed (${e.javaClass.simpleName}). Try another video or retry." }
            catch (_: OutOfMemoryError) { result = "Not enough memory. Close other apps and retry." }
            main.post { busy = false; detail = result; onChanged?.invoke() }
        }
    }
    fun cancel() { cancelled.set(true); if (busy) onChanged?.invoke() }
    override fun onCleared() { cancelled.set(true); onChanged = null; worker.shutdownNow() }
}
