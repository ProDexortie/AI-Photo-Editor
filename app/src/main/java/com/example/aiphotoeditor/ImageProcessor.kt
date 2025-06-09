package com.example.aiphotoeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.pow

class ImageProcessor {

    // --- Существующие фильтры ---

    fun applySepia(bitmap: Bitmap): Bitmap {
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    fun applyVintage(bitmap: Bitmap): Bitmap {
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.6f, 0.3f, 0.1f, 0f, 20f,
                0.2f, 0.5f, 0.3f, 0f, 20f,
                0.2f, 0.3f, 0.5f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // --- Новые фильтры ---

    fun applyCoolFilter(bitmap: Bitmap): Bitmap {
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.25f, 0f, 10f, // Увеличиваем синий
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    fun applyInvert(bitmap: Bitmap): Bitmap {
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    fun applySolarize(bitmap: Bitmap, threshold: Int = 128): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff

            // ИСПРАВЛЕНИЕ: Добавляем .toInt() к шестнадцатеричным литералам
            if (r < threshold) pixels[i] = pixels[i] and (0x00FFFFFF).inv() or ((255 - r) shl 16)
            if (g < threshold) pixels[i] = pixels[i] and (0xFF00FF00.toInt()).inv() or ((255 - g) shl 8)
            if (b < threshold) pixels[i] = pixels[i] and (0xFFFF00FF.toInt()).inv() or (255 - b)
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }


    // --- Существующие параметры ---

    fun adjustBrightness(bitmap: Bitmap, value: Float): Bitmap {
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, value,
                0f, 1f, 0f, 0f, value,
                0f, 0f, 1f, 0f, value,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val translate = (-.5f * contrast + .5f) * 255f
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    fun adjustSaturation(bitmap: Bitmap, saturation: Float): Bitmap {
        val colorMatrix = ColorMatrix().apply { setSaturation(saturation) }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // --- Новые параметры ---

    fun adjustTemperature(bitmap: Bitmap, temperature: Float): Bitmap {
        val kelvin = temperature.coerceIn(-100f, 100f)
        val redFactor = if (kelvin > 0) 1f + 0.005f * kelvin else 1f
        val blueFactor = if (kelvin < 0) 1f - 0.005f * kelvin else 1f

        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                redFactor, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, blueFactor, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    fun applySharpen(bitmap: Bitmap, strength: Float): Bitmap {
        if (strength == 0f) return bitmap
        return adjustContrast(bitmap, 1 + (strength / 2f))
    }

    fun adjustVibrance(bitmap: Bitmap, vibrance: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            Color.colorToHSV(pixels[i], hsv)
            val saturation = hsv[1]
            val amount = (1 - saturation).pow(2) * vibrance * 0.1f
            hsv[1] = (saturation + amount).coerceIn(0f, 1f)
            pixels[i] = Color.HSVToColor(pixels[i] and (0xFF shl 24), hsv)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }


    private fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
}