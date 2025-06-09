package com.example.aiphotoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StyleTransferHelper(private val context: Context) {

    companion object {
        private const val MODEL_PATH = "style_transfer.tflite"
        private const val CONTENT_IMAGE_SIZE = 384
        private const val STYLE_BOTTLENECK_SIZE = 100
        private const val OUTPUT_SIZE = 384
        private const val PIXEL_SIZE = 3
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            initializeModel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializeModel() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            interpreter = null
        }
    }

    // ИЗМЕНЕНИЕ: Метод теперь возвращает финальное изображение в высоком разрешении
    fun processImage(contentBitmap: Bitmap, styleVector: FloatArray): Bitmap? {
        if (interpreter == null) return null

        // 1. Получаем стилизованное изображение в низком разрешении
        val stylizedLowResBitmap = processWithTensorFlow(contentBitmap, styleVector)

        // 2. Если стилизация прошла успешно, рекомбинируем его с оригиналом для сохранения разрешения
        return stylizedLowResBitmap?.let {
            recombineWithOriginalResolution(contentBitmap, it)
        }
    }

    private fun processWithTensorFlow(contentBitmap: Bitmap, styleVector: FloatArray): Bitmap? {
        try {
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(CONTENT_IMAGE_SIZE, CONTENT_IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0.0f, 255.0f))
                .build()

            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(contentBitmap)
            tensorImage = imageProcessor.process(tensorImage)

            val styleBuffer = ByteBuffer.allocateDirect(4 * STYLE_BOTTLENECK_SIZE)
            styleBuffer.order(ByteOrder.nativeOrder())
            styleVector.forEach { styleValue ->
                styleBuffer.putFloat(styleValue)
            }
            styleBuffer.rewind()

            val inputs = arrayOf(tensorImage.buffer, styleBuffer)

            val outputBuffer = ByteBuffer.allocateDirect(4 * OUTPUT_SIZE * OUTPUT_SIZE * PIXEL_SIZE)
            outputBuffer.order(ByteOrder.nativeOrder())
            val outputs = mapOf(0 to outputBuffer)

            interpreter!!.runForMultipleInputsOutputs(inputs, outputs)

            return bufferToBitmap(outputBuffer, OUTPUT_SIZE, OUTPUT_SIZE)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // НОВЫЙ МЕТОД: для рекомбинации детализации и стиля
    private fun recombineWithOriginalResolution(original: Bitmap, stylizedLowRes: Bitmap): Bitmap {
        val originalWidth = original.width
        val originalHeight = original.height

        // Растягиваем стилизованное изображение до оригинального размера
        val upscaledStylized = Bitmap.createScaledBitmap(stylizedLowRes, originalWidth, originalHeight, true)

        val originalPixels = IntArray(originalWidth * originalHeight)
        original.getPixels(originalPixels, 0, originalWidth, 0, 0, originalWidth, originalHeight)

        val stylizedPixels = IntArray(originalWidth * originalHeight)
        upscaledStylized.getPixels(stylizedPixels, 0, originalWidth, 0, 0, originalWidth, originalHeight)

        val finalPixels = IntArray(originalWidth * originalHeight)

        val originalHsv = FloatArray(3)
        val stylizedHsv = FloatArray(3)

        for (i in finalPixels.indices) {
            // Конвертируем оба пикселя в HSV
            Color.colorToHSV(originalPixels[i], originalHsv)
            Color.colorToHSV(stylizedPixels[i], stylizedHsv)

            // Берем Hue и Saturation от стилизованного, а Value от оригинального
            val finalHsv = floatArrayOf(stylizedHsv[0], stylizedHsv[1], originalHsv[2])

            // Конвертируем обратно в RGB и записываем в финальный массив
            finalPixels[i] = Color.HSVToColor(finalHsv)
        }

        return Bitmap.createBitmap(finalPixels, originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
    }


    private fun bufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        // ... (этот метод без изменений)
        buffer.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (i in 0 until width * height) {
            val r = (buffer.float * 255).toInt().coerceIn(0, 255)
            val g = (buffer.float * 255).toInt().coerceIn(0, 255)
            val b = (buffer.float * 255).toInt().coerceIn(0, 255)
            pixels[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun close() {
        interpreter?.close()
    }
}