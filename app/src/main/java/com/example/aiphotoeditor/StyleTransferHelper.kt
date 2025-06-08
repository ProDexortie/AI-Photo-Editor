package com.example.aiphotoeditor

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StyleTransferHelper(private val context: Context) {

    companion object {
        private const val MODEL_PATH = "style_transfer_model.tflite"
        private const val INPUT_SIZE = 256
        private const val OUTPUT_SIZE = 256
        private const val PIXEL_SIZE = 3
    }

    private var interpreter: Interpreter? = null
    private var imageProcessor: ImageProcessor? = null

    init {
        try {
            initializeModel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializeModel() {
        try {
            // Попытка загрузить модель из assets
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
            interpreter = Interpreter(modelBuffer)

            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .build()

        } catch (e: Exception) {
            // Если модель не найдена, используем простую имитацию AI обработки
            interpreter = null
            imageProcessor = null
        }
    }

    fun processImage(bitmap: Bitmap): Bitmap {
        return if (interpreter != null && imageProcessor != null) {
            processWithTensorFlow(bitmap)
        } else {
            // Fallback: имитация AI обработки с помощью комбинации фильтров
            processWithSimulatedAI(bitmap)
        }
    }

    private fun processWithTensorFlow(bitmap: Bitmap): Bitmap {
        try {
            // Подготовка входного изображения
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val processedImage = imageProcessor!!.process(tensorImage)

            // Создание входного буфера
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
            inputBuffer.order(ByteOrder.nativeOrder())

            // Копирование данных изображения в буфер
            val buffer = processedImage.buffer
            inputBuffer.put(buffer)
            inputBuffer.rewind()

            // Создание выходного буфера
            val outputBuffer = ByteBuffer.allocateDirect(4 * OUTPUT_SIZE * OUTPUT_SIZE * PIXEL_SIZE)
            outputBuffer.order(ByteOrder.nativeOrder())

            // Выполнение инференса
            interpreter!!.run(inputBuffer, outputBuffer)

            // Преобразование результата обратно в Bitmap
            return bufferToBitmap(outputBuffer, OUTPUT_SIZE, OUTPUT_SIZE)

        } catch (e: Exception) {
            e.printStackTrace()
            return processWithSimulatedAI(bitmap)
        }
    }

    private fun processWithSimulatedAI(bitmap: Bitmap): Bitmap {
        // Имитация AI обработки через комбинацию фильтров
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)

        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // Применяем "AI-подобную" обработку
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff

            // Эффект "масляной живописи"
            val newR = enhanceChannel(r, 1.1f, 15)
            val newG = enhanceChannel(g, 1.05f, 10)
            val newB = enhanceChannel(b, 1.15f, 20)

            pixels[i] = (0xff shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)

        // Добавляем легкое размытие для эффекта
        return applyGaussianBlur(result, 1f)
    }

    private fun enhanceChannel(value: Int, multiplier: Float, boost: Int): Int {
        val enhanced = (value * multiplier + boost).toInt()
        return enhanced.coerceIn(0, 255)
    }

    private fun applyGaussianBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)

        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // Простое приближение Gaussian blur
        val blurredPixels = IntArray(width * height)
        val radiusInt = radius.toInt()

        for (y in 0 until height) {
            for (x in 0 until width) {
                var totalR = 0
                var totalG = 0
                var totalB = 0
                var count = 0

                for (dy in -radiusInt..radiusInt) {
                    for (dx in -radiusInt..radiusInt) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val pixel = pixels[ny * width + nx]

                        totalR += (pixel shr 16) and 0xff
                        totalG += (pixel shr 8) and 0xff
                        totalB += pixel and 0xff
                        count++
                    }
                }

                val avgR = totalR / count
                val avgG = totalG / count
                val avgB = totalB / count

                blurredPixels[y * width + x] = (0xff shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
            }
        }

        result.setPixels(blurredPixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun bufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
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