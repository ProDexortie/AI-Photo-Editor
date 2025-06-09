package com.example.aiphotoeditor

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer

class StylePredictionHelper(private val context: Context) {

    companion object {
        // ИЗМЕНЕНО: Имя файла модели
        private const val MODEL_PATH = "style_predict.tflite"
        private const val INPUT_IMAGE_SIZE = 256
        private const val STYLE_VECTOR_SIZE = 100
    }

    // ... остальной код файла остается без изменений ...
    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getStyleVector(bitmap: Bitmap): FloatArray? {
        if (interpreter == null) return null

        try {
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0.0f, 255.0f))
                .build()

            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            val styleVector = Array(1) { Array(1) { Array(1) { FloatArray(STYLE_VECTOR_SIZE) } } }
            val outputs = mapOf(0 to styleVector)

            interpreter?.runForMultipleInputsOutputs(arrayOf(tensorImage.buffer), outputs)

            return styleVector[0][0][0]

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun close() {
        interpreter?.close()
    }
}