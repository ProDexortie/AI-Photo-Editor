package com.example.aiphotoeditor

import android.graphics.*

class ImageProcessor {

    fun applySepia(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(result, colorMatrix)
    }

    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        return applyColorMatrix(result, colorMatrix)
    }

    fun applyVintage(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.6f, 0.3f, 0.1f, 0f, 20f,
                0.2f, 0.5f, 0.3f, 0f, 20f,
                0.2f, 0.3f, 0.5f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(result, colorMatrix)
    }

    fun applyColdFilter(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.8f, 0f, 0f, 0f, 0f,
                0f, 0.9f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(result, colorMatrix)
    }

    fun applyWarmFilter(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.1f, 0f, 0f, 5f,
                0f, 0f, 0.8f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(result, colorMatrix)
    }

    fun adjustBrightness(bitmap: Bitmap, value: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, value,
                0f, 1f, 0f, 0f, value,
                0f, 0f, 1f, 0f, value,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(result, colorMatrix)
    }

    fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val translate = (-.5f * contrast + .5f) * 255f
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(result, colorMatrix)
    }

    fun adjustSaturation(bitmap: Bitmap, saturation: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val colorMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }
        return applyColorMatrix(result, colorMatrix)
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