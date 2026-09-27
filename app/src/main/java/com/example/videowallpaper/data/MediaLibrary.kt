package com.example.videowallpaper.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.example.videowallpaper.random.RandomWallpaperPreferences
import com.example.videowallpaper.random.RotationOrder

data class MediaEntry(val uri: String, val name: String, val video: Boolean)

/** Draft edits never modify the applied playlist until Set wallpaper is pressed. */
class MediaLibrary(private val context: Context, storageName: String = "media_library", migrateLegacy: Boolean = true) {
    private val prefs = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    init {
        if (migrateLegacy && !prefs.contains("draft")) {
            val legacy = WallpaperPreferences(context)
            val entries = mutableListOf<MediaEntry>()
            legacy.getVideoUri()?.let { entries.add(MediaEntry(it.toString(), com.example.videowallpaper.util.DocumentUtils.getDisplayName(context, it), true)) }
            val old = RandomWallpaperPreferences(context)
            old.images().forEachIndexed { i, uri -> entries.add(MediaEntry(uri, old.names().getOrNull(i) ?: "Image", false)) }
            setEntries(entries.distinctBy { it.uri }.take(50))
            legacy.preparedUri()?.let { prepared -> legacy.getVideoUri()?.let { cache(it.toString(), File(prepared.path!!)) } }
            prefs.edit().putBoolean("pingpong", legacy.isPingPong()).apply()
            legacy.getVideoUri()?.let { uri ->
                val oldEntry = entries.first { it.uri == uri.toString() }
                val json = JSONArray().put(JSONObject().put("uri", oldEntry.uri).put("name", oldEntry.name).put("video", true))
                prefs.edit().putString("active", json.toString()).putString("current", oldEntry.uri)
                    .putBoolean("active_pingpong", legacy.isPingPong()).apply()
            }
        }
    }
    fun entries(active: Boolean = false, preview: Boolean = false): List<MediaEntry> = runCatching {
        val json = JSONArray(prefs.getString(if (preview) "pending" else if (active) "active" else "draft", "[]"))
        (0 until json.length()).map { json.getJSONObject(it).let { MediaEntry(it.getString("uri"), it.getString("name"), it.getBoolean("video")) } }
    }.getOrDefault(emptyList())
    fun setEntries(items: List<MediaEntry>) {
        require(items.size <= 50)
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("uri", it.uri).put("name", it.name).put("video", it.video)) }
        prefs.edit().putString("draft", array.toString()).apply()
    }
    /** Upgrade the editor without overwriting the already applied wallpaper. */
    fun useSingleVideo() {
        val items = entries()
        val video = items.firstOrNull { it.video } ?: current()?.takeIf { it.video }
        val selection = listOfNotNull(video)
        if (items != selection) setEntries(selection)
        prefs.edit().putBoolean("rotate", false).putBoolean("active_rotate", false)
            .putBoolean("pending_rotate", false).apply()
        gamma = (kotlin.math.round(gamma * 2f) / 2f).coerceIn(.5f, 8f)
    }
    var pingPong: Boolean
        get() = prefs.getBoolean("pingpong", false)
        set(value) { prefs.edit().putBoolean("pingpong", value).apply() }
    var rotate: Boolean
        get() = prefs.getBoolean("rotate", false)
        set(value) { prefs.edit().putBoolean("rotate", value).apply() }
    var target: Int
        get() = prefs.getInt("target", 1)
        set(value) { prefs.edit().putInt("target", value).apply() }
    var mode: RotationOrder.Mode
        get() = RotationOrder.Mode.valueOf(prefs.getString("mode", "SHUFFLE")!!)
        set(value) { prefs.edit().putString("mode", value.name).apply() }
    var gamma: Float
        get() = prefs.getFloat("gamma", 1f).coerceIn(.5f, 8f)
        set(value) { prefs.edit().putFloat("gamma", value.coerceIn(.5f, 8f)).apply() }
    fun activeGamma() = prefs.getFloat("active_gamma", 1f).coerceIn(.5f, 8f)
    fun prepared(uri: String, requestedGamma: Float = gamma): Uri? {
        val name = prefs.getString("cache:$uri:${gammaKey(requestedGamma)}", null) ?: return null
        if (File(name).name != name) return null
        return File(context.filesDir, "pingpong/$name").takeIf { it.isFile && it.length() > 0 }?.let(Uri::fromFile)
    }
    fun cache(uri: String, file: File, gamma: Float = this.gamma) { prefs.edit().putString("cache:$uri:${gammaKey(gamma)}", file.name).apply() }
    private fun gammaKey(value: Float = gamma) = "%.2f".format(java.util.Locale.US, value)
    fun playback(entry: MediaEntry, active: Boolean = false, preview: Boolean = false): Uri? =
        if (entry.video && (if (preview) prefs.getBoolean("pending_pingpong", false) else if (active) prefs.getBoolean("active_pingpong", false) else pingPong)) prepared(entry.uri, if (preview) prefs.getFloat("pending_gamma", 1f) else if (active) activeGamma() else gamma) else Uri.parse(entry.uri)
    fun current(preview: Boolean = false): MediaEntry? = entries(true, preview).firstOrNull { it.uri == prefs.getString(if (preview) "pending_current" else "current", null) } ?: entries(true, preview).firstOrNull()
    fun isLive() = entries(true).any { it.video }
    fun activeTarget() = prefs.getInt("active_target", 1)
    fun activeRotate() = prefs.getBoolean("active_rotate", false)
    fun stage(index: Int) {
        val items = entries()
        require(items.isNotEmpty())
        prefs.edit().putString("pending", prefs.getString("draft", "[]"))
            .putString("pending_current", items[index.coerceIn(items.indices)].uri)
            .putBoolean("pending_pingpong", pingPong).putInt("pending_target", target)
            .putBoolean("pending_rotate", rotate).putString("pending_mode", mode.name).commit()
        prefs.edit().putFloat("pending_gamma", gamma).commit()
    }
    fun commitPending() {
        prefs.edit().putString("active", prefs.getString("pending", "[]"))
            .putString("current", prefs.getString("pending_current", null))
            .putBoolean("active_pingpong", prefs.getBoolean("pending_pingpong", false))
            .putInt("active_target", prefs.getInt("pending_target", 1))
            .putBoolean("active_rotate", prefs.getBoolean("pending_rotate", false))
            .putString("active_mode", prefs.getString("pending_mode", "SHUFFLE"))
            .putFloat("active_gamma", prefs.getFloat("pending_gamma", 1f))
            .putString("remaining", "[]").putLong("revision", System.nanoTime()).commit()
    }
    fun nextChoice(): RotationOrder.Choice? {
        val bag = JSONArray(prefs.getString("remaining", "[]"))
        return RotationOrder.next(entries(true).map { it.uri }, current()?.uri,
            (0 until bag.length()).map { bag.getString(it) }, RotationOrder.Mode.valueOf(prefs.getString("active_mode", "SHUFFLE")!!))
    }
    fun advance(choice: RotationOrder.Choice) {
        prefs.edit().putString("current", choice.uri).putString("remaining", JSONArray(choice.remaining).toString()).putLong("revision", System.nanoTime()).apply()
    }
    /** Called outside preparation; keep media still used by either draft or applied wallpaper. */
    fun pruneUnused() {
        val retained = (entries() + entries(true) + entries(preview = true)).map { it.uri }.toSet()
        context.contentResolver.persistedUriPermissions.filter { it.uri.toString() !in retained }.forEach {
            runCatching { context.contentResolver.releasePersistableUriPermission(it.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val editor = prefs.edit()
        prefs.all.filterKeys { it.startsWith("cache:") && it.substringBeforeLast(":") .removePrefix("cache:") !in retained }.forEach { (key, value) ->
            val name = value as? String
            if (name != null && File(name).name == name) File(context.filesDir, "pingpong/$name").delete()
            editor.remove(key)
        }
        editor.apply()
    }
    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
}
