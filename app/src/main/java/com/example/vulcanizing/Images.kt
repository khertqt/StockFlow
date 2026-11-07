package com.example.vulcanizing

import androidx.compose.ui.unit.dp
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Small in-memory cache so photos load quickly while scrolling
object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(path: String): Bitmap? = if (path.isBlank()) null else cache.get(path)

    fun put(path: String, bmp: Bitmap) {
        cache.put(path, bmp)
    }
}

// Reads a saved photo file at a small size for lists and cards
fun decodeFileScaled(path: String, maxSide: Int = 400): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        BitmapFactory.decodeFile(path, opts)
    } catch (e: Throwable) {
        null
    }
}

// Reads a photo from the camera or gallery, fixes its rotation and shrinks it
// so the saved file stays small (longest side 800 px)
fun loadScaledBitmap(context: Context, uri: Uri, maxSide: Int = 800): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        var degrees = 0f
        try {
            resolver.openInputStream(uri)?.use { s ->
                val o = ExifInterface(s).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
                degrees = when (o) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        } catch (e: Exception) {
        }

        val longest = maxOf(decoded.width, decoded.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        if (scale < 1f || degrees != 0f) {
            val m = Matrix()
            m.postScale(scale, scale)
            if (degrees != 0f) m.postRotate(degrees)
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        } else {
            decoded
        }
    } catch (e: Throwable) {
        null
    }
}

// Shows the product photo, or a category icon when the product has no photo.
// Give it a size with the modifier; the photo fills and crops to that size.
@Composable
fun ProductImage(
    path: String,
    category: String,
    modifier: Modifier = Modifier,
    emojiSize: TextUnit = 28.sp
) {
    val bmp by produceState<Bitmap?>(initialValue = ImageCache.get(path), key1 = path) {
        value = if (path.isBlank()) {
            null
        } else {
            ImageCache.get(path) ?: withContext(Dispatchers.IO) {
                decodeFileScaled(path)?.also { ImageCache.put(path, it) }
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp.let { it }))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Product photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(categoryEmoji(category), fontSize = emojiSize)
        }
    }
}