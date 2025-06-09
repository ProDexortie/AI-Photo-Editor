package com.example.aiphotoeditor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aiphotoeditor.databinding.ActivityImageEditBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageEditBinding
    private var originalBitmap: Bitmap? = null
    private var editedBitmap: Bitmap? = null
    private lateinit var imageProcessor: ImageProcessor
    // Удаляем инициализацию хелпера отсюда

    // Лаунчер для выбора картинки-стиля
    private val styleImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                applyAiStyleTransfer(uri)
            }
        }
    }

    private enum class FilterType {
        ORIGINAL, SEPIA, GRAYSCALE, VINTAGE, COLD, WARM,
        BRIGHTNESS_UP, BRIGHTNESS_DOWN, CONTRAST_UP, CONTRAST_DOWN,
        SATURATION_UP, SATURATION_DOWN, AI_STYLE_TRANSFER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        imageProcessor = ImageProcessor() // Инициализируем только ImageProcessor
        loadImage()
        setupFilters()
        setupControls()
    }

    private fun applyAiStyleTransfer(styleImageUri: Uri) {
        showProgress(true)
        lifecycleScope.launch {
            try {
                // Загружаем картинку стиля
                val styleBitmap = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(styleImageUri)
                    BitmapFactory.decodeStream(inputStream)
                }

                // 1. Получаем вектор стиля
                val stylePredictionHelper = StylePredictionHelper(this@ImageEditActivity)
                val styleVector = withContext(Dispatchers.IO) {
                    stylePredictionHelper.getStyleVector(styleBitmap)
                }
                stylePredictionHelper.close() // Закрываем хелпер

                if (styleVector == null) {
                    throw Exception("Failed to generate style vector.")
                }

                // 2. Применяем стиль к основному изображению
                val styleTransferHelper = StyleTransferHelper(this@ImageEditActivity)
                val finalBitmap = withContext(Dispatchers.IO) {
                    originalBitmap?.let {
                        styleTransferHelper.processImage(it, styleVector)
                    }
                }
                styleTransferHelper.close() // Закрываем хелпер

                if (finalBitmap != null) {
                    editedBitmap = finalBitmap
                    binding.ivImage.setImageBitmap(editedBitmap)
                } else {
                    throw Exception("Failed to apply style.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ImageEditActivity, getString(R.string.failed_to_apply_filter), Toast.LENGTH_SHORT).show()
            } finally {
                showProgress(false)
            }
        }
    }

    private fun setupFilters() {
        val filters = listOf(
            // ... (все ваши старые фильтры)
            getString(R.string.filter_original) to FilterType.ORIGINAL,
            getString(R.string.filter_sepia) to FilterType.SEPIA,
            getString(R.string.filter_bw) to FilterType.GRAYSCALE,
            getString(R.string.filter_vintage) to FilterType.VINTAGE,
            getString(R.string.filter_cold) to FilterType.COLD,
            getString(R.string.filter_warm) to FilterType.WARM,
            getString(R.string.filter_bright_up) to FilterType.BRIGHTNESS_UP,
            getString(R.string.filter_bright_down) to FilterType.BRIGHTNESS_DOWN,
            getString(R.string.filter_contrast_up) to FilterType.CONTRAST_UP,
            getString(R.string.filter_contrast_down) to FilterType.CONTRAST_DOWN,
            getString(R.string.filter_vibrant_up) to FilterType.SATURATION_UP,
            getString(R.string.filter_vibrant_down) to FilterType.SATURATION_DOWN,
            getString(R.string.filter_ai_style) to FilterType.AI_STYLE_TRANSFER
        )

        filters.forEach { (name, type) ->
            val button = MaterialButton(this).apply {
                text = name
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(8, 0, 8, 0) }

                setOnClickListener {
                    if (type == FilterType.AI_STYLE_TRANSFER) {
                        // Запускаем выбор картинки для стиля
                        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        intent.type = "image/*"
                        styleImageLauncher.launch(intent)
                    } else {
                        applyFilter(type)
                    }
                }
            }
            binding.llFilters.addView(button)
        }
    }

    private fun applyFilter(filterType: FilterType) {
        originalBitmap?.let { bitmap ->
            showProgress(true)
            lifecycleScope.launch {
                try {
                    val processed = withContext(Dispatchers.IO) {
                        when (filterType) {
                            FilterType.ORIGINAL -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
                            FilterType.SEPIA -> imageProcessor.applySepia(bitmap)
                            FilterType.GRAYSCALE -> imageProcessor.applyGrayscale(bitmap)
                            FilterType.VINTAGE -> imageProcessor.applyVintage(bitmap)
                            FilterType.COLD -> imageProcessor.applyColdFilter(bitmap)
                            FilterType.WARM -> imageProcessor.applyWarmFilter(bitmap)
                            FilterType.BRIGHTNESS_UP -> imageProcessor.adjustBrightness(bitmap, 30f)
                            FilterType.BRIGHTNESS_DOWN -> imageProcessor.adjustBrightness(bitmap, -30f)
                            FilterType.CONTRAST_UP -> imageProcessor.adjustContrast(bitmap, 1.3f)
                            FilterType.CONTRAST_DOWN -> imageProcessor.adjustContrast(bitmap, 0.7f)
                            FilterType.SATURATION_UP -> imageProcessor.adjustSaturation(bitmap, 1.5f)
                            FilterType.SATURATION_DOWN -> imageProcessor.adjustSaturation(bitmap, 0.5f)
                            else -> editedBitmap // Для AI_STYLE_TRANSFER теперь отдельная логика
                        }
                    }
                    editedBitmap = processed
                    binding.ivImage.setImageBitmap(processed)
                } catch (e: Exception) {
                    Toast.makeText(this@ImageEditActivity, getString(R.string.failed_to_apply_filter), Toast.LENGTH_SHORT).show()
                } finally {
                    showProgress(false)
                }
            }
        }
    }

    // --- Остальные методы (setupToolbar, loadImage, setupControls, и т.д.) остаются без изменений ---

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadImage() {
        val imageUriString = intent.getStringExtra("image_uri")
        if (imageUriString != null) {
            try {
                val uri = Uri.parse(imageUriString)
                val inputStream = contentResolver.openInputStream(uri)
                originalBitmap = BitmapFactory.decodeStream(inputStream)
                editedBitmap = originalBitmap?.copy(Bitmap.Config.ARGB_8888, true)

                binding.ivImage.setImageBitmap(editedBitmap)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, getString(R.string.no_image_provided), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupControls() {
        binding.btnReset.setOnClickListener {
            resetImage()
        }

        binding.btnSave.setOnClickListener {
            saveImage()
        }
    }

    private fun resetImage() {
        originalBitmap?.let { bitmap ->
            editedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            binding.ivImage.setImageBitmap(editedBitmap)
        }
    }

    private fun saveImage() {
        editedBitmap?.let { bitmap ->
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val saved = MediaStore.Images.Media.insertImage(
                            contentResolver,
                            bitmap,
                            "AI_Photo_Editor_${System.currentTimeMillis()}",
                            "Edited with AI Photo Editor"
                        )
                        if (saved == null) {
                            throw IOException("Failed to save image")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageEditActivity, getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ImageEditActivity, getString(R.string.image_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        originalBitmap?.recycle()
        editedBitmap?.recycle()
    }
}