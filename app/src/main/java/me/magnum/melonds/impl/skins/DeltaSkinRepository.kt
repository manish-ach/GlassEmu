package me.magnum.melonds.impl.skins

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Imports Nintendo DS Delta archives into private storage; no URI permission is needed afterwards. */
class DeltaSkinRepository(private val context: Context) {
    data class Choice(val id: String, val name: String)
    data class Representation(val json: JSONObject, val artwork: File?, val assetName: String, val cacheKey: String)
    private val root = File(context.filesDir, "delta-skins")
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun choices(): List<Choice> = listOf(Choice("none", context.getString(me.magnum.melonds.R.string.skin_default_controls)), Choice("builtin", context.getString(me.magnum.melonds.R.string.skin_builtin_name))) +
        root.listFiles().orEmpty().filter { File(it, "skin.json").isFile }.mapNotNull { dir ->
            runCatching { Choice(dir.name, JSONObject(File(dir, "skin.json").readText()).getString("name")) }.getOrNull()
        }.sortedBy { it.name.lowercase() }

    fun selected(): String = preferences.getString("delta_skin", "builtin") ?: "builtin"

    fun representation(portrait: Boolean, aspect: Float): Representation? {
        val id = selected()
        if (id == "none") return null
        if (id == "builtin") {
            if (!portrait) return landscape()
            val json = JSONObject(context.assets.open("skins/glassemu/portrait.json").bufferedReader().use { it.readText() })
            return Representation(json, null, "skins/glassemu/portrait.pdf", "builtin")
        }
        if (choices().none { it.id == id }) return null
        val dir = File(root, id)
        return runCatching {
            val variants = JSONObject(File(dir, "skin.json").readText()).getJSONArray("variants")
            val candidates = (0 until variants.length()).map { variants.getJSONObject(it) }.filter { it.getBoolean("portrait") == portrait }
            val best = candidates.minByOrNull {
                val size = it.getJSONObject("mappingSize")
                kotlin.math.abs(size.getDouble("width") / size.getDouble("height") - aspect)
            } ?: return@runCatching if (!portrait && (0 until variants.length()).any {
                variants.getJSONObject(it).optJSONObject("assets")?.optString("resizable") == "iphone_edgetoedge_portrait.pdf"
            }) landscape() else null
            val file = File(dir, best.getString("asset"))
            Representation(best, file, file.name, "$id/${file.name}")
        }.getOrNull()
    }

    private fun landscape(): Representation {
        val json = JSONObject(context.assets.open("skins/glassemu/landscape.json").bufferedReader().use { it.readText() })
        return Representation(json, null, "skins/glassemu/landscape.png", "builtin-landscape")
    }

    fun import(uri: Uri): String {
        root.mkdirs()
        val archive = File.createTempFile("delta-", ".zip", context.cacheDir)
        val stage = File(root, ".import-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                archive.outputStream().use { out -> copyLimited(input, out, 64L * 1024 * 1024) }
            } ?: error("The selected file could not be opened.")
            val variants = org.json.JSONArray()
            val id = UUID.randomUUID().toString()
            stage.mkdirs()
            val name = ZipFile(archive).use { zip ->
                val infoEntry = zip.getEntry("info.json") ?: error("This archive has no info.json.")
                val infoBytes = java.io.ByteArrayOutputStream()
                zip.getInputStream(infoEntry).use { copyLimited(it, infoBytes, 2L * 1024 * 1024) }
                val info = JSONObject(infoBytes.toString("UTF-8"))
                require(info.optString("gameTypeIdentifier") == "com.rileytestut.delta.game.ds") { "Choose a Nintendo DS skin." }
                var expandedBytes = 0L
                fun visit(node: JSONObject) {
                    node.keys().forEach { key ->
                        val child = node.optJSONObject(key) ?: return@forEach
                        if (key == "portrait" || key == "landscape") {
                            val assets = child.optJSONObject("assets") ?: return@forEach
                            val candidateNames = listOf("resizable", "large", "medium", "small") + assets.keys().asSequence().toList()
                            val source = candidateNames.mapNotNull { assets.opt(it) as? String }.firstOrNull {
                                (it.endsWith(".pdf", true) || it.endsWith(".png", true)) && zip.getEntry(it) != null
                            } ?: return@forEach
                            // Extract only supported, referenced artwork to generated flat filenames.
                            validate(child)
                            val entry = zip.getEntry(source)
                            val asset = "asset-${variants.length()}.${if (source.endsWith(".pdf", true)) "pdf" else "png"}"
                            val file = File(stage, asset)
                            zip.getInputStream(entry).use { input -> file.outputStream().use { out -> copyLimited(input, out, 32L * 1024 * 1024) } }
                            expandedBytes += file.length()
                            require(expandedBytes <= 64L * 1024 * 1024) { "The skin artwork is too large." }
                            // Validate the actual image before publishing the imported skin.
                            if (asset.endsWith(".pdf")) {
                                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                    android.graphics.pdf.PdfRenderer(fd).use { renderer -> require(renderer.pageCount > 0) { "The skin PDF is empty." } }
                                }
                            } else {
                                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                android.graphics.BitmapFactory.decodeFile(file.path, options)
                                require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 32_000_000) { "The skin image is invalid or too large." }
                            }
                            val variant = JSONObject(child.toString()).put("portrait", key == "portrait").put("asset", asset)
                            val items = variant.getJSONArray("items")
                            for (i in 0 until items.length()) {
                                val stick = items.getJSONObject(i).optJSONObject("thumbstick") ?: continue
                                val stickEntry = zip.getEntry(stick.getString("name")) ?: error("Missing thumbstick artwork.")
                                val stickName = "stick-${variants.length()}-$i.png"
                                val stickFile = File(stage, stickName)
                                zip.getInputStream(stickEntry).use { input -> stickFile.outputStream().use { out -> copyLimited(input, out, 4L * 1024 * 1024) } }
                                expandedBytes += stickFile.length()
                                require(expandedBytes <= 64L * 1024 * 1024) { "The skin artwork is too large." }
                                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                android.graphics.BitmapFactory.decodeFile(stickFile.path, bounds)
                                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 4_000_000) { "Invalid thumbstick artwork." }
                                require(stick.getDouble("width").let { it.isFinite() && it in 1.0..4096.0 } && stick.getDouble("height").let { it.isFinite() && it in 1.0..4096.0 }) { "Invalid thumbstick size." }
                                stick.put("name", stickName)
                            }
                            variants.put(variant)
                        } else visit(child)
                    }
                }
                visit(info.getJSONObject("representations"))
                require(variants.length() > 0) { "This skin has no supported DS layout with PDF or PNG artwork." }
                info.optString("name", "Imported skin").take(120)
            }
            File(stage, "skin.json").writeText(JSONObject().put("name", name).put("variants", variants).toString())
            require(stage.renameTo(File(root, id))) { "The skin could not be saved." }
            preferences.edit().putString("delta_skin", id).apply()
            return name
        } finally { archive.delete(); if (stage.exists()) stage.deleteRecursively() }
    }

    private fun validate(json: JSONObject) {
        val mapping = json.getJSONObject("mappingSize")
        val w = mapping.getDouble("width"); val h = mapping.getDouble("height")
        require(w.isFinite() && h.isFinite() && w in 1.0..4096.0 && h in 1.0..4096.0) { "Invalid skin size." }
        val screens = json.getJSONArray("screens")
        require(screens.length() == 2) { "The skin must provide two DS screens." }
        fun frame(rect: JSONObject) {
            val x = rect.getDouble("x"); val y = rect.getDouble("y"); val rw = rect.getDouble("width"); val rh = rect.getDouble("height")
            require(listOf(x,y,rw,rh).all { it.isFinite() } && x >= 0 && y >= 0 && rw > 0 && rh > 0 && x + rw <= w + 1 && y + rh <= h + 1) { "Invalid skin frame." }
        }
        for (i in 0 until screens.length()) {
            val screen = screens.getJSONObject(i)
            frame(screen.getJSONObject("outputFrame"))
            require(!screen.has("filters")) { "This skin uses unsupported screen effects." }
            val input = screen.optJSONObject("inputFrame")
            if (input != null) require(input.optInt("x") == 0 && input.optInt("y") == i * 192 && input.optInt("width") == 256 && input.optInt("height") == 192) { "Cropped or reordered screen layouts are not supported yet." }
        }
        val items = json.getJSONArray("items")
        require(items.length() <= 128) { "Too many skin controls." }
        for (i in 0 until items.length()) frame(items.getJSONObject(i).getJSONObject("frame"))
    }

    private fun copyLimited(input: java.io.InputStream, out: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(8192); var total = 0L
        while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= limit) { "The skin file is too large." }; out.write(buffer, 0, count) }
    }
}
