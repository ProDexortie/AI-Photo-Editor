package com.example.aiphotoeditor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.example.aiphotoeditor.databinding.ActivityImageEditBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageEditBinding

    private var originalBitmap: Bitmap? = null
    private var editedBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null

    private lateinit var imageProcessor: ImageProcessor
    private enum class EditingTool { NONE, BRIGHTNESS, CONTRAST, SATURATION, TEMPERATURE, SHARPNESS, VIBRANCE }
    private var currentEditingTool = EditingTool.NONE
    private var isShowingSubPanel = false
    private var isShowingSlider = false

    private val styleImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> applyAiStyleTransfer(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageProcessor = ImageProcessor()
        setupToolbar()
        loadImage()
        setupMainControls()
        handleBackButton()
    }

    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.edit_image)
        binding.toolbar.inflateMenu(R.menu.image_edit_menu)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_save -> {
                    saveImage()
                    true
                }
                else -> false
            }
        }
    }

    private fun handleBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isShowingSlider -> hideSlider()
                    isShowingSubPanel -> hideSubToolsPanel()
                    else -> finish()
                }
            }
        })
    }

    private fun setupMainControls() {
        binding.mainControlsPanel.removeAllViews()
        addControlButton(R.drawable.ic_photo_camera, getString(R.string.tools)) { showSubToolsPanel(isFilters = false) }
        addControlButton(R.drawable.ic_photo_library, getString(R.string.filters)) { showSubToolsPanel(isFilters = true) }
        addControlButton(R.drawable.ic_camera, getString(R.string.ai_style)) { selectAiStyleImage() }
    }

    private fun showSubToolsPanel(isFilters: Boolean) {
        isShowingSubPanel = true
        binding.subToolsPanel.removeAllViews()

        val title = if (isFilters) getString(R.string.filters) else getString(R.string.tools)
        addPanelTitleWithBack(binding.subToolsPanel, title) { hideSubToolsPanel() }

        val toolsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        if (isFilters) {
            addTool(toolsLayout, getString(R.string.filter_grayscale)) { applyImmediateFilter { imageProcessor.applyGrayscale(it) } }
            addTool(toolsLayout, getString(R.string.filter_sepia)) { applyImmediateFilter { imageProcessor.applySepia(it) } }
            addTool(toolsLayout, getString(R.string.filter_vintage)) { applyImmediateFilter { imageProcessor.applyVintage(it) } }
            addTool(toolsLayout, getString(R.string.filter_cool)) { applyImmediateFilter { imageProcessor.applyCoolFilter(it) } }
            addTool(toolsLayout, getString(R.string.filter_invert)) { applyImmediateFilter { imageProcessor.applyInvert(it) } }
            addTool(toolsLayout, getString(R.string.reset)) { resetImage() }
        } else {
            addTool(toolsLayout, getString(R.string.tool_brightness)) { showSliderFor(EditingTool.BRIGHTNESS) }
            addTool(toolsLayout, getString(R.string.tool_contrast)) { showSliderFor(EditingTool.CONTRAST) }
            addTool(toolsLayout, getString(R.string.tool_saturation)) { showSliderFor(EditingTool.SATURATION) }
            addTool(toolsLayout, getString(R.string.tool_temperature)) { showSliderFor(EditingTool.TEMPERATURE) }
            addTool(toolsLayout, getString(R.string.tool_vibrance)) { showSliderFor(EditingTool.VIBRANCE) }
            addTool(toolsLayout, getString(R.string.tool_sharpness)) { showSliderFor(EditingTool.SHARPNESS) }
        }

        binding.subToolsPanel.addView(toolsLayout)

        binding.mainControlsPanel.visibility = View.GONE
        binding.subToolsPanel.visibility = View.VISIBLE
        binding.subToolsPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom))
    }

    private fun showSliderFor(tool: EditingTool) {
        currentEditingTool = tool
        isShowingSlider = true
        binding.sliderPanel.removeAllViews()

        previewBitmap = editedBitmap?.let { Bitmap.createScaledBitmap(it, (it.width * 0.4).toInt(), (it.height * 0.4).toInt(), true) }

        val toolName = when (tool) {
            EditingTool.BRIGHTNESS -> getString(R.string.tool_brightness)
            EditingTool.CONTRAST -> getString(R.string.tool_contrast)
            EditingTool.SATURATION -> getString(R.string.tool_saturation)
            EditingTool.TEMPERATURE -> getString(R.string.tool_temperature)
            EditingTool.VIBRANCE -> getString(R.string.tool_vibrance)
            EditingTool.SHARPNESS -> getString(R.string.tool_sharpness)
            else -> ""
        }
        addPanelTitleWithBack(binding.sliderPanel, toolName) { hideSlider() }

        val slider = Slider(this).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(96, 32, 96, 96)
            layoutParams = params

            when (tool) {
                EditingTool.BRIGHTNESS -> { valueFrom = -100f; valueTo = 100f; stepSize = 1f; value = 0f }
                EditingTool.CONTRAST -> { valueFrom = 0.5f; valueTo = 2.0f; stepSize = 0.05f; value = 1.0f }
                EditingTool.SATURATION -> { valueFrom = 0.0f; valueTo = 2.0f; stepSize = 0.05f; value = 1.0f }
                EditingTool.TEMPERATURE -> { valueFrom = -100f; valueTo = 100f; stepSize = 1f; value = 0f }
                EditingTool.VIBRANCE -> { valueFrom = -100f; valueTo = 100f; stepSize = 1f; value = 0f }
                EditingTool.SHARPNESS -> { valueFrom = 0f; valueTo = 1f; stepSize = 0.05f; value = 0f }
                else -> {}
            }
        }

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) applySliderEffect(value, applyToFullRes = false)
        }

        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { applySliderEffect(slider.value, applyToFullRes = true) }
        })

        binding.sliderPanel.addView(slider)

        binding.subToolsPanel.visibility = View.GONE
        binding.sliderPanel.visibility = View.VISIBLE
        binding.sliderPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom))
    }

    private fun applySliderEffect(value: Float, applyToFullRes: Boolean) {
        val baseBitmap = if (applyToFullRes) originalBitmap else previewBitmap

        baseBitmap?.let { bmp ->
            lifecycleScope.launch(Dispatchers.IO) {
                val result = when (currentEditingTool) {
                    EditingTool.BRIGHTNESS -> imageProcessor.adjustBrightness(bmp, value)
                    EditingTool.CONTRAST -> imageProcessor.adjustContrast(bmp, value)
                    EditingTool.SATURATION -> imageProcessor.adjustSaturation(bmp, value)
                    EditingTool.TEMPERATURE -> imageProcessor.adjustTemperature(bmp, value)
                    EditingTool.VIBRANCE -> imageProcessor.adjustVibrance(bmp, value / 100f)
                    EditingTool.SHARPNESS -> imageProcessor.applySharpen(bmp, value)
                    else -> bmp
                }

                withContext(Dispatchers.Main) {
                    if (applyToFullRes) editedBitmap = result
                    binding.ivImage.setImageBitmap(result)
                }
            }
        }
    }

    private fun hideSlider() {
        isShowingSlider = false
        currentEditingTool = EditingTool.NONE
        binding.sliderPanel.visibility = View.GONE
        showSubToolsPanel(isFilters = false)
        binding.ivImage.setImageBitmap(editedBitmap)
    }

    private fun hideSubToolsPanel() {
        isShowingSubPanel = false
        binding.subToolsPanel.visibility = View.GONE
        binding.mainControlsPanel.visibility = View.VISIBLE
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
                Toast.makeText(this, getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, getString(R.string.no_image_provided), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun applyImmediateFilter(filter: (Bitmap) -> Bitmap) {
        originalBitmap?.let {
            showProgress(true)
            lifecycleScope.launch(Dispatchers.IO) {
                editedBitmap = filter(it)
                withContext(Dispatchers.Main) {
                    binding.ivImage.setImageBitmap(editedBitmap)
                    showProgress(false)
                }
            }
        }
    }

    private fun selectAiStyleImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        styleImageLauncher.launch(intent)
    }

    private fun applyAiStyleTransfer(styleImageUri: Uri) {
        showProgress(true)
        lifecycleScope.launch {
            try {
                val styleBitmap = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(styleImageUri)
                    BitmapFactory.decodeStream(inputStream)
                }

                val stylePredictionHelper = StylePredictionHelper(this@ImageEditActivity)
                val styleVector = withContext(Dispatchers.IO) { stylePredictionHelper.getStyleVector(styleBitmap) }
                stylePredictionHelper.close()

                if (styleVector == null) throw Exception(getString(R.string.failed_to_generate_style_vector))

                val styleTransferHelper = StyleTransferHelper(this@ImageEditActivity)
                val finalBitmap = withContext(Dispatchers.IO) {
                    originalBitmap?.let { styleTransferHelper.processImage(it, styleVector) }
                }
                styleTransferHelper.close()

                if (finalBitmap == null) throw Exception(getString(R.string.failed_to_apply_ai_style))

                editedBitmap = finalBitmap
                binding.ivImage.setImageBitmap(editedBitmap)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ImageEditActivity, e.message ?: getString(R.string.failed_to_apply_filter), Toast.LENGTH_SHORT).show()
            } finally {
                showProgress(false)
            }
        }
    }

    private fun resetImage() {
        editedBitmap = originalBitmap?.copy(Bitmap.Config.ARGB_8888, true)
        binding.ivImage.setImageBitmap(editedBitmap)
        Toast.makeText(this, getString(R.string.image_reset_success), Toast.LENGTH_SHORT).show()
    }

    private fun saveImage() {
        editedBitmap?.let { bitmap ->
            showProgress(true)
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val saved = MediaStore.Images.Media.insertImage(
                            contentResolver,
                            bitmap,
                            "AI_Photo_Editor_${System.currentTimeMillis()}",
                            "Edited with AI Photo Editor"
                        )
                        if (saved == null) throw IOException(getString(R.string.image_save_failed))
                    }
                    Toast.makeText(this@ImageEditActivity, getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ImageEditActivity, e.message, Toast.LENGTH_SHORT).show()
                } finally {
                    showProgress(false)
                }
            }
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun addControlButton(iconRes: Int, text: String, onClick: () -> Unit) {
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = params
            setOnClickListener { onClick() }
        }

        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(this@ImageEditActivity, R.color.primary_text))
        }

        val textView = TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@ImageEditActivity, R.color.secondary_text))
            textSize = 12f
        }

        buttonLayout.addView(icon)
        buttonLayout.addView(textView)
        binding.mainControlsPanel.addView(buttonLayout)
    }

    private fun addTool(parent: LinearLayout, text: String, onClick: () -> Unit) {
        val textView = TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@ImageEditActivity, R.color.primary_text))
            setPadding(24, 24, 24, 24)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }
        parent.addView(textView)
    }

    private fun addPanelTitleWithBack(parent: LinearLayout, title: String, onBack: () -> Unit) {
        // Добавьте сюда свою реализацию заголовка, если требуется
    }

    override fun onDestroy() {
        super.onDestroy()
        originalBitmap?.recycle()
        editedBitmap?.recycle()
        previewBitmap?.recycle()
    }
}