package me.magnum.melonds.ui.emulator

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.ui.emulator.input.IInputListener
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Delta skin: artwork, screen frames and hit areas share one transform. */
internal class DeltaSkinView(
    context: Context,
    private val skin: DeltaSkinLayout,
    private val system: IInputListener?,
    private val frontend: IInputListener?,
    private val touchFrame: () -> Rect,
    private val hapticFeedback: () -> Unit,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val darkSkinFilter = if (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
        // Tint only the skin artwork; alpha and the emulator screens underneath remain intact.
        android.graphics.ColorMatrixColorFilter(floatArrayOf(
            -0.1836f, -0.3605f, -0.0709f, 0f, 221f,
            -0.1836f, -0.3605f, -0.0709f, 0f, 204f,
            -0.1836f, -0.3605f, -0.0709f, 0f, 243f,
            0f, 0f, 0f, 1f, 0f,
        ))
    } else null

    private val targets = mutableMapOf<Int, DeltaSkinLayout.Item>()
    private val touchPointers = mutableSetOf<Int>()
    private var pressed = emptySet<Input>()
    private val stickPositions = mutableMapOf<DeltaSkinLayout.Item, android.graphics.PointF>()
    private var fastForwardHeld = false
    private fun updateHold(held: Boolean) {
        if (fastForwardHeld != held) { fastForwardHeld = held; frontend?.onFastForwardHoldChanged(held) }
    }
    var fastForwardEnabled = false
        set(value) { field = value; invalidate() }
    var hiddenKeys: Set<Input> = emptySet()
        set(value) { field = value; updateKeys(pressed - value) }
    var buttonsEnabled = true
        set(value) { field = value; if (!value) { targets.clear(); stickPositions.clear(); updateHold(false); updateKeys(emptySet()); invalidate() } }

    override fun onDraw(canvas: Canvas) {
        paint.colorFilter = darkSkinFilter
        canvas.drawBitmap(skin.artwork, null, skin.artworkFrame, paint)
        canvas.save()
        canvas.translate(skin.artworkFrame.left, skin.artworkFrame.top)
        canvas.scale(skin.artworkFrame.width() / skin.mappingWidth, skin.artworkFrame.width() / skin.mappingWidth)
        skin.items.forEach { item ->
            item.thumbstick?.let { bitmap ->
                val point = stickPositions[item]
                val cx = item.frame.centerX() + (point?.x ?: 0f)
                val cy = item.frame.centerY() + (point?.y ?: 0f)
                if (skin.isWhiteLandscape) {
                    if (point == null) return@let
                    paint.color = 0xFFEDEDED.toInt()
                    canvas.drawCircle(cx, cy, item.stickWidth / 2, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = 0xFF333333.toInt()
                    canvas.drawCircle(cx, cy, item.stickWidth / 2, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = android.graphics.Color.WHITE
                } else canvas.drawBitmap(bitmap, null, RectF(cx - item.stickWidth / 2, cy - item.stickHeight / 2, cx + item.stickWidth / 2, cy + item.stickHeight / 2), paint)
            }
        }
        canvas.restore()
        skin.fastForwardFrame?.let { frame ->
            canvas.save()
            canvas.translate(skin.artworkFrame.left, skin.artworkFrame.top)
            canvas.scale(skin.artworkFrame.width() / skin.mappingWidth, skin.artworkFrame.width() / skin.mappingWidth)
            val button = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = darkSkinFilter }
            button.color = android.graphics.Color.argb(45, 0, 0, 0)
            canvas.drawRoundRect(RectF(frame.left, frame.top + 2, frame.right, frame.bottom + 2), 3f, 3f, button)
            button.shader = android.graphics.LinearGradient(0f, frame.top, 0f, frame.bottom,
                if (fastForwardEnabled) intArrayOf(0xFFF4EAFF.toInt(), 0xFFE0CFF5.toInt()) else intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6E5E8.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRoundRect(frame, 3f, 3f, button)
            button.shader = null
            button.style = Paint.Style.STROKE; button.strokeWidth = 0.8f; button.color = 0xFF4F4C53.toInt()
            canvas.drawRoundRect(frame, 3f, 3f, button)
            button.style = Paint.Style.FILL; button.color = if (fastForwardEnabled) 0xFF7050A0.toInt() else 0xFF77727C.toInt()
            for (offset in listOf(-7f, 1f)) {
                val x = frame.centerX() + offset
                val arrow = android.graphics.Path().apply { moveTo(x, frame.centerY() - 6); lineTo(x + 7, frame.centerY()); lineTo(x, frame.centerY() + 6); close() }
                canvas.drawPath(arrow, button)
            }
            canvas.restore()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) { releaseAll(); return true }
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val i = event.actionIndex
            val id = event.getPointerId(i)
            val frame = touchFrame()
            if (event.getX(i) >= frame.x && event.getX(i) < frame.right && event.getY(i) >= frame.y && event.getY(i) < frame.bottom) {
                touchPointers += id
            } else if (buttonsEnabled) {
                val x = skin.mappingX(event.getX(i)); val y = skin.mappingY(event.getY(i))
                val item = skin.items.filter { it.frame.contains(x, y) }.minByOrNull { it.frame.width() * it.frame.height() }
                    ?: skin.items.filter { it.hitFrame.contains(x, y) }.minByOrNull { abs(it.frame.centerX() - x) + abs(it.frame.centerY() - y) }
                if (item != null) targets[id] = item
            }
        }
        val ending = if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        val keys = mutableSetOf<Input>()
        var touch: Point? = null
        var held = false
        stickPositions.clear()
        for (i in 0 until event.pointerCount) {
            val id = event.getPointerId(i)
            if (i == ending) { targets.remove(id); touchPointers.remove(id); continue }
            if (id in touchPointers && touch == null) {
                val frame = touchFrame()
                touch = Point(((event.getX(i) - frame.x) / frame.width * 256).toInt().coerceIn(0, 255), ((event.getY(i) - frame.y) / frame.height * 192).toInt().coerceIn(0, 191))
            }
            targets[id]?.let { item ->
                val x = skin.mappingX(event.getX(i)); val y = skin.mappingY(event.getY(i))
                if (item.thumbstick != null || item.hitFrame.contains(x, y)) {
                    if (item.holdFastForward && Input.FAST_FORWARD !in hiddenKeys) held = true
                    if (item.dpad) {
                        val dx = (x - item.frame.centerX()) / (item.frame.width() / 2)
                        val dy = (y - item.frame.centerY()) / (item.frame.height() / 2)
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                        val directionX = if (item.thumbstick != null) dx / distance else dx
                        val directionY = if (item.thumbstick != null) dy / distance else dy
                        if (directionX < -0.3f) item.directions["left"]?.let { keys += it }
                        if (directionX > 0.3f) item.directions["right"]?.let { keys += it }
                        if (directionY < -0.3f) item.directions["up"]?.let { keys += it }
                        if (directionY > 0.3f) item.directions["down"]?.let { keys += it }
                        if (item.thumbstick != null) {
                            val travel = if (skin.isWhiteLandscape) 20f else min(item.frame.width(), item.frame.height()) * 0.32f
                            stickPositions[item] = android.graphics.PointF(dx / distance * travel, dy / distance * travel)
                        }
                    } else keys += item.keys
                }
            }
        }
        if (touch != null) keys += Input.TOUCHSCREEN
        updateHold(held)
        if (skin.items.any { it.thumbstick != null }) invalidate()
        updateKeys(keys - hiddenKeys)
        touch?.let { system?.onTouch(it) }
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    private fun updateKeys(keys: Set<Input>) {
        (pressed - keys).forEach { key ->
            (if (key.isSystemInput) system else frontend)?.onKeyReleased(key)
            if (key == Input.TOUCHSCREEN) MelonEmulator.onScreenRelease()
        }
        if ((keys - pressed).any { it != Input.TOUCHSCREEN }) hapticFeedback()
        (keys - pressed).forEach { key -> (if (key.isSystemInput) system else frontend)?.onKeyPress(key) }
        pressed = keys.toSet()
    }

    fun releaseAll() { targets.clear(); touchPointers.clear(); stickPositions.clear(); updateHold(false); updateKeys(emptySet()); invalidate() }
    override fun onDetachedFromWindow() { releaseAll(); super.onDetachedFromWindow() }
    override fun performClick(): Boolean { super.performClick(); return true }
}

internal class DeltaSkinLayout(context: Context, width: Int, height: Int, private val representation: me.magnum.melonds.impl.skins.DeltaSkinRepository.Representation) {
    data class Item(val frame: RectF, val hitFrame: RectF, val keys: List<Input>, val dpad: Boolean, val directions: Map<String, Input> = emptyMap(), val thumbstick: Bitmap? = null, val stickWidth: Float = 0f, val stickHeight: Float = 0f, val holdFastForward: Boolean = false)
    val isWhiteLandscape = representation.cacheKey == "builtin-landscape"
    private val json = representation.json
    private val mapping = json.getJSONObject("mappingSize")
    private val scale = min(width / mapping.getDouble("width").toFloat(), height / mapping.getDouble("height").toFloat())
    private val offsetX = (width - mapping.getDouble("width").toFloat() * scale) / 2
    private val offsetY = (height - mapping.getDouble("height").toFloat() * scale) / 2
    val artworkFrame = RectF(offsetX, offsetY, width - offsetX, height - offsetY)
    val screens = (0 until json.getJSONArray("screens").length()).map { i ->
        val r = frame(json.getJSONArray("screens").getJSONObject(i).getJSONObject("outputFrame"))
        Rect((offsetX + r.left * scale).roundToInt(), (offsetY + r.top * scale).roundToInt(), (r.width() * scale).roundToInt(), (r.height() * scale).roundToInt())
    }
    val mappingWidth = mapping.getDouble("width").toFloat()
    private val dsiSupplement = if (representation.cacheKey == "builtin" ||
        (mappingWidth == 375f && mapping.getDouble("height") == 812.0 && json.optJSONObject("assets")?.optString("resizable") == "iphone_edgetoedge_portrait.pdf")) RectF(329f, 756f, 357f, 784f) else null
    val items = (0 until json.getJSONArray("items").length()).mapNotNull { i ->
        val item = json.getJSONArray("items").getJSONObject(i)
        val inputs = item.get("inputs")
        if (inputs is JSONObject && inputs.has("touchScreenX")) return@mapNotNull null
        if (inputs is JSONObject && inputs.has("x")) return@mapNotNull null
        val dpad = inputs is JSONObject && inputs.has("up")
        val directions = if (dpad) listOf("up", "down", "left", "right").mapNotNull { direction ->
            Input.entries.firstOrNull { it.name.equals((inputs as JSONObject).optString(direction), true) }?.let { direction to it }
        }.toMap() else emptyMap()
        val holdFastForward = inputs is org.json.JSONArray && (0 until inputs.length()).any { inputs.optString(it) == "fastForward" }
        val keys = if (dpad) emptyList() else (0 until item.getJSONArray("inputs").length()).mapNotNull { j ->
            when (val name = item.getJSONArray("inputs").getString(j)) {
                "menu" -> Input.PAUSE
                "quickSave" -> Input.QUICK_SAVE
                "quickLoad" -> Input.QUICK_LOAD
                "fastForward" -> null
                "toggleFastForward" -> Input.FAST_FORWARD
                "microphone" -> Input.MICROPHONE
                "swapScreens" -> Input.SWAP_SCREENS
                else -> Input.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
        }
        val r = frame(item.getJSONObject("frame"))
        val edges = item.optJSONObject("extendedEdges") ?: JSONObject()
        val defaults = json.optJSONObject("extendedEdges") ?: JSONObject()
        fun edge(name: String) = edges.optDouble(name, defaults.optDouble(name, 0.0)).toFloat()
        Item(r, RectF(r.left - edge("left"), r.top - edge("top"), r.right + edge("right"), r.bottom + edge("bottom")), keys, dpad, directions, item.optJSONObject("thumbstick")?.let { stick ->
            if (representation.artwork != null) android.graphics.BitmapFactory.decodeFile(File(representation.artwork.parentFile, stick.getString("name")).path)
            else context.assets.open(representation.assetName.substringBeforeLast('/') + "/" + stick.getString("name")).use { android.graphics.BitmapFactory.decodeStream(it) }
        }, item.optJSONObject("thumbstick")?.optDouble("width")?.toFloat() ?: 0f, item.optJSONObject("thumbstick")?.optDouble("height")?.toFloat() ?: 0f, holdFastForward)
    }.let { items ->
        val frame = dsiSupplement
        if (frame != null && items.none { Input.FAST_FORWARD in it.keys }) items + Item(frame, RectF(frame.left - 8, frame.top - 8, frame.right + 8, frame.bottom + 8), listOf(Input.FAST_FORWARD), false) else items
    }
    val fastForwardFrame: RectF? = dsiSupplement?.takeIf { frame -> items.lastOrNull()?.frame == frame }
    val artwork: Bitmap = cachedArtwork?.takeIf { cachedKey == representation.cacheKey } ?: render(context).also { cachedArtwork = it; cachedKey = representation.cacheKey }
    fun mappingX(x: Float) = (x - offsetX) / scale
    fun mappingY(y: Float) = (y - offsetY) / scale
    private fun frame(j: JSONObject): RectF {
        val x = j.getDouble("x").toFloat(); val y = j.getDouble("y").toFloat()
        return RectF(x, y, x + j.getDouble("width").toFloat(), y + j.getDouble("height").toFloat())
    }
    private fun render(context: Context): Bitmap {
        val file = representation.artwork ?: File(context.cacheDir, "dsi-white-portrait.pdf").also { file ->
            context.assets.open(representation.assetName).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val rasterWidth = if (isWhiteLandscape) 2160 else 1080
        val bitmap = Bitmap.createBitmap(rasterWidth, (rasterWidth * mapping.getDouble("height") / mapping.getDouble("width")).roundToInt().coerceIn(1, 4096), Bitmap.Config.ARGB_8888)
        if (representation.assetName.endsWith(".pdf", true)) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> renderer.openPage(0).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } }
            }
        } else {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.path, options)
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (options.outWidth / options.inSampleSize > 2160 || options.outHeight / options.inSampleSize > 4096) options.inSampleSize *= 2
            val image = requireNotNull(android.graphics.BitmapFactory.decodeFile(file.path, options))
            val canvas = Canvas(bitmap)
            canvas.drawBitmap(image, null, RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            image.recycle()
        }
        // Screen openings must expose the emulator's existing SurfaceView underneath.
        val canvas = Canvas(bitmap)
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val factor = bitmap.width / mapping.getDouble("width").toFloat()
        for (i in 0 until json.getJSONArray("screens").length()) {
            val r = frame(json.getJSONArray("screens").getJSONObject(i).getJSONObject("outputFrame"))
            canvas.drawRect(r.left * factor, r.top * factor, r.right * factor, r.bottom * factor, clear)
        }
        return bitmap
    }
    companion object { private var cachedArtwork: Bitmap? = null; private var cachedKey: String? = null }
}
