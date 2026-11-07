package com.example.vulcanizing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.OptIn
import android.graphics.Color as AColor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

fun barcodeBitmap(text: String, width: Int = 600, height: Int = 200): Bitmap? {
    return try {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.CODE_128, width, height)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (matrix.get(x, y)) AColor.BLACK else AColor.WHITE
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }
}

// A printable label: product name, barcode image, barcode text and SKU
fun labelBitmap(p: Product): Bitmap? {
    val bar = barcodeBitmap(p.barcode) ?: return null
    val w = bar.width + 80
    val h = bar.height + 170
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(AColor.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AColor.BLACK
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 36f
    paint.isFakeBoldText = true
    c.drawText(p.name.take(28), w / 2f, 55f, paint)
    c.drawBitmap(bar, 40f, 75f, null)
    paint.textSize = 30f
    paint.isFakeBoldText = false
    c.drawText(p.barcode, w / 2f, 75f + bar.height + 40f, paint)
    c.drawText("SKU: " + p.id, w / 2f, 75f + bar.height + 80f, paint)
    return out
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScanner(modifier: Modifier = Modifier, onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as LifecycleOwner
    val currentOnCode by rememberUpdatedState(onCode)
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                providerHolder[0]?.unbindAll()
                scanner.close()
            } catch (e: Exception) {
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            val previewView = PreviewView(c)
            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
                try {
                    val provider = future.get()
                    providerHolder[0] = provider
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(c)) { proxy ->
                        val media = proxy.image
                        if (media != null) {
                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { codes ->
                                    val raw = codes.firstOrNull()?.rawValue
                                    if (raw != null) currentOnCode(raw)
                                }
                                .addOnCompleteListener { proxy.close() }
                        } else {
                            proxy.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                }
            }, ContextCompat.getMainExecutor(c))
            previewView
        }
    )
}