package com.luminex.photoenhancer.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.luminex.photoenhancer.R
import com.luminex.photoenhancer.adapters.FilterAdapter
import com.luminex.photoenhancer.database.AppDatabase
import com.luminex.photoenhancer.databinding.ActivityPhotoEditorBinding
import com.luminex.photoenhancer.models.Photo
import com.luminex.photoenhancer.utils.ImageEnhancer
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhotoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoEditorBinding
    private lateinit var database: AppDatabase
    private lateinit var photoEditor: ja.burhanrashid52.photoeditor.PhotoEditor

    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var imagePath: String = ""

    // Adjustment values
    private var brightness: Float = 0f
    private var contrast: Float = 1f
    private var saturation: Float = 1f
    private var sharpness: Float = 0f
    private var exposure: Float = 0f
    private var highlights: Float = 0.5f
    private var shadows: Float = 0.5f
    private var temperature: Float = 5000f
    private var tint: Float = 0f
    private var hue: Float = 0f
    private var vignetteIntensity: Float = 0f

    private var currentFilter: String = "None"
    private var currentEditMode: EditMode = EditMode.FILTERS

    private enum class EditMode {
        FILTERS, ADJUST, CROP, DRAW, TEXT, BEAUTY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)
        imagePath = intent.getStringExtra("IMAGE_PATH") ?: ""

        if (imagePath.isEmpty()) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        loadImage()
        setupClickListeners()
        setupTabs()
        setupFilters()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit Photo"

        binding.toolbar.setNavigationOnClickListener {
            showExitConfirmation()
        }

        // Initialize PhotoEditor
        photoEditor = ja.burhanrashid52.photoeditor.PhotoEditor.Builder(this, binding.photoEditorView)
            .setPinchTextScalable(true)
            .build()
    }

    private fun loadImage() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            withContext(Dispatchers.IO) {
                try {
                    val bitmap = if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                        // Already a URI
                        val uri = Uri.parse(imagePath)
                        val inputStream = contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream)
                    } else {
                        // File path - load directly
                        BitmapFactory.decodeFile(imagePath)
                    }

                    originalBitmap = bitmap
                    currentBitmap = bitmap?.copy(
                        bitmap.config ?: Bitmap.Config.ARGB_8888,
                        true
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            binding.progressBar.visibility = View.GONE
            currentBitmap?.let {
                binding.photoEditorView.source.setImageBitmap(it)
            } ?: run {
                Toast.makeText(this@PhotoEditorActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Filters").setIcon(R.drawable.ic_filter))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Adjust").setIcon(R.drawable.ic_tune))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Crop").setIcon(R.drawable.ic_crop))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Draw").setIcon(R.drawable.ic_brush))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Text").setIcon(R.drawable.ic_text))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Beauty").setIcon(R.drawable.ic_face))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showFiltersPanel()
                    1 -> showAdjustPanel()
                    2 -> startCrop()
                    3 -> showDrawPanel()
                    4 -> showTextPanel()
                    5 -> showBeautyPanel()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupClickListeners() {
        // Top action buttons
        binding.btnSave.setOnClickListener { saveImage() }
        binding.btnShare.setOnClickListener { shareImage() }
        binding.btnReset.setOnClickListener { resetImage() }
        binding.btnAutoEnhance.setOnClickListener { autoEnhance() }
        binding.btnCompare.setOnClickListener { compareBeforeAfter() }

        // Adjustment controls
        binding.btnApplyAdjustment.setOnClickListener { applyCurrentAdjustment() }
        binding.btnResetAdjustment.setOnClickListener { resetCurrentAdjustment() }

        // Seekbar listener
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateAdjustmentPreview(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                applyCurrentAdjustment()
            }
        })
    }

    private fun setupFilters() {
        val filters = ImageEnhancer.getAvailableFilters()
        val filterAdapter = FilterAdapter(filters) { filterName ->
            applyFilter(filterName)
        }

        binding.recyclerFilters.apply {
            layoutManager = LinearLayoutManager(
                this@PhotoEditorActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = filterAdapter
        }
    }

    // ==================== PANEL SWITCHING ====================

    private fun showFiltersPanel() {
        currentEditMode = EditMode.FILTERS
        binding.filtersPanel.visibility = View.VISIBLE
        binding.adjustPanel.visibility = View.GONE
        binding.cropPanel.visibility = View.GONE
        binding.drawPanel.visibility = View.GONE
        binding.textPanel.visibility = View.GONE
        binding.beautyPanel.visibility = View.GONE
        photoEditor.setBrushDrawingMode(false)
    }

    private fun showAdjustPanel() {
        currentEditMode = EditMode.ADJUST
        binding.filtersPanel.visibility = View.GONE
        binding.adjustPanel.visibility = View.VISIBLE
        binding.cropPanel.visibility = View.GONE
        binding.drawPanel.visibility = View.GONE
        binding.textPanel.visibility = View.GONE
        binding.beautyPanel.visibility = View.GONE
        photoEditor.setBrushDrawingMode(false)

        setupAdjustmentButtons()
    }

    private fun showCropPanel() {
        currentEditMode = EditMode.CROP
        binding.filtersPanel.visibility = View.GONE
        binding.adjustPanel.visibility = View.GONE
        binding.cropPanel.visibility = View.VISIBLE
        binding.drawPanel.visibility = View.GONE
        binding.textPanel.visibility = View.GONE
        binding.beautyPanel.visibility = View.GONE
        photoEditor.setBrushDrawingMode(false)
    }

    private fun showDrawPanel() {
        currentEditMode = EditMode.DRAW
        binding.filtersPanel.visibility = View.GONE
        binding.adjustPanel.visibility = View.GONE
        binding.cropPanel.visibility = View.GONE
        binding.drawPanel.visibility = View.VISIBLE
        binding.textPanel.visibility = View.GONE
        binding.beautyPanel.visibility = View.GONE

        photoEditor.setBrushDrawingMode(true)
    }

    private fun showTextPanel() {
        currentEditMode = EditMode.TEXT
        binding.filtersPanel.visibility = View.GONE
        binding.adjustPanel.visibility = View.GONE
        binding.cropPanel.visibility = View.GONE
        binding.drawPanel.visibility = View.GONE
        binding.textPanel.visibility = View.VISIBLE
        binding.beautyPanel.visibility = View.GONE
        photoEditor.setBrushDrawingMode(false)

        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Add Text")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                photoEditor.addText(input.text.toString(), R.color.white)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBeautyPanel() {
        currentEditMode = EditMode.BEAUTY
        binding.filtersPanel.visibility = View.GONE
        binding.adjustPanel.visibility = View.GONE
        binding.cropPanel.visibility = View.GONE
        binding.drawPanel.visibility = View.GONE
        binding.textPanel.visibility = View.GONE
        binding.beautyPanel.visibility = View.VISIBLE

        applyBeautyEffect()
    }

    private fun setupAdjustmentButtons() {
        binding.btnBrightness.setOnClickListener { showAdjustmentControl("Brightness") }
        binding.btnContrast.setOnClickListener { showAdjustmentControl("Contrast") }
        binding.btnSaturation.setOnClickListener { showAdjustmentControl("Saturation") }
        binding.btnSharpness.setOnClickListener { showAdjustmentControl("Sharpness") }
        binding.btnExposure.setOnClickListener { showAdjustmentControl("Exposure") }
        binding.btnHighlights.setOnClickListener { showAdjustmentControl("Highlights") }
        binding.btnShadows.setOnClickListener { showAdjustmentControl("Shadows") }
        binding.btnTemperature.setOnClickListener { showAdjustmentControl("Temperature") }
        binding.btnHue.setOnClickListener { showAdjustmentControl("Hue") }
        binding.btnVignette.setOnClickListener { showAdjustmentControl("Vignette") }
    }

    // ==================== CROP & ROTATE ====================

    private fun startCrop() {
        val sourceUri = Uri.fromFile(File(imagePath))
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1000, 1000)
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = data?.let { UCrop.getOutput(it) }
            resultUri?.let {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val croppedBitmap = BitmapFactory.decodeStream(inputStream)
                    currentBitmap = croppedBitmap
                    binding.photoEditorView.source.setImageBitmap(currentBitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to load cropped image", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = data?.let { UCrop.getError(it) }
            cropError?.printStackTrace()
            Toast.makeText(this, "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== FILTER APPLICATION ====================

    private fun applyFilter(filterName: String) {
        if (filterName == "Auto") {
            autoEnhance()
            return
        }

        currentFilter = filterName

        if (filterName == "None") {
            resetImage()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            currentBitmap = withContext(Dispatchers.Default) {
                originalBitmap?.let {
                    ImageEnhancer.applyFilter(this@PhotoEditorActivity, it, filterName)
                }
            }

            binding.progressBar.visibility = View.GONE
            currentBitmap?.let {
                binding.photoEditorView.source.setImageBitmap(it)
            }
        }
    }

    // ==================== ADJUSTMENT CONTROLS ====================

    private fun showAdjustmentControl(type: String) {
        binding.adjustmentControlPanel.visibility = View.VISIBLE
        binding.txtAdjustmentName.text = type

        // Set seekbar to current value
        when (type) {
            "Brightness" -> binding.seekBar.progress = ((brightness + 1f) * 50).toInt()
            "Contrast" -> binding.seekBar.progress = (contrast * 50).toInt()
            "Saturation" -> binding.seekBar.progress = (saturation * 50).toInt()
            "Sharpness" -> binding.seekBar.progress = (sharpness * 100).toInt()
            "Exposure" -> binding.seekBar.progress = ((exposure + 2f) * 25).toInt()
            "Highlights" -> binding.seekBar.progress = (highlights * 100).toInt()
            "Shadows" -> binding.seekBar.progress = (shadows * 100).toInt()
            "Temperature" -> binding.seekBar.progress = ((temperature - 2000f) / 60f).toInt()
            "Hue" -> binding.seekBar.progress = ((hue + 180f) / 3.6f).toInt()
            "Vignette" -> binding.seekBar.progress = (vignetteIntensity * 100).toInt()
        }
    }

    private fun updateAdjustmentPreview(progress: Int) {
        val type = binding.txtAdjustmentName.text.toString()

        when (type) {
            "Brightness" -> brightness = (progress / 50f) - 1f
            "Contrast" -> contrast = progress / 50f
            "Saturation" -> saturation = progress / 50f
            "Sharpness" -> sharpness = progress / 100f
            "Exposure" -> exposure = (progress / 25f) - 2f
            "Highlights" -> highlights = progress / 100f
            "Shadows" -> shadows = progress / 100f
            "Temperature" -> temperature = 2000f + (progress * 60f)
            "Hue" -> hue = (progress * 3.6f) - 180f
            "Vignette" -> vignetteIntensity = progress / 100f
        }

        binding.txtAdjustmentValue.text = when (type) {
            "Brightness", "Exposure" -> String.format("%.2f", if (type == "Brightness") brightness else exposure)
            "Temperature" -> "${temperature.toInt()}K"
            "Hue" -> "${hue.toInt()}°"
            else -> "${(progress / 100f * 100).toInt()}%"
        }
    }

    private fun applyCurrentAdjustment() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            currentBitmap = withContext(Dispatchers.Default) {
                originalBitmap?.let { original ->
                    var result = ImageEnhancer.enhanceBitmap(
                        original,
                        brightness,
                        contrast,
                        saturation,
                        sharpness
                    )

                    // Apply advanced adjustments
                    if (exposure != 0f) {
                        result = ImageEnhancer.adjustExposure(this@PhotoEditorActivity, result, exposure)
                    }

                    if (highlights != 0.5f || shadows != 0.5f) {
                        result = ImageEnhancer.adjustHighlightsShadows(
                            this@PhotoEditorActivity,
                            result,
                            shadows,
                            highlights
                        )
                    }

                    if (temperature != 5000f || tint != 0f) {
                        result = ImageEnhancer.adjustWhiteBalance(
                            this@PhotoEditorActivity,
                            result,
                            temperature,
                            tint
                        )
                    }

                    if (hue != 0f) {
                        result = ImageEnhancer.adjustHue(this@PhotoEditorActivity, result, hue)
                    }

                    if (vignetteIntensity > 0f) {
                        result = ImageEnhancer.addVignette(
                            this@PhotoEditorActivity,
                            result,
                            start = 0.3f,
                            end = 0.75f - (vignetteIntensity * 0.5f)
                        )
                    }

                    result
                }
            }

            binding.progressBar.visibility = View.GONE
            currentBitmap?.let {
                binding.photoEditorView.source.setImageBitmap(it)
            }
        }
    }

    private fun resetCurrentAdjustment() {
        val type = binding.txtAdjustmentName.text.toString()

        when (type) {
            "Brightness" -> brightness = 0f
            "Contrast" -> contrast = 1f
            "Saturation" -> saturation = 1f
            "Sharpness" -> sharpness = 0f
            "Exposure" -> exposure = 0f
            "Highlights" -> highlights = 0.5f
            "Shadows" -> shadows = 0.5f
            "Temperature" -> temperature = 5000f
            "Hue" -> hue = 0f
            "Vignette" -> vignetteIntensity = 0f
        }

        binding.seekBar.progress = 50
        applyCurrentAdjustment()
    }

    // ==================== BEAUTY EFFECTS ====================

    private fun applyBeautyEffect() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            currentBitmap = withContext(Dispatchers.Default) {
                originalBitmap?.let {
                    ImageEnhancer.smoothSkin(this@PhotoEditorActivity, it, 0.7f)
                }
            }

            binding.progressBar.visibility = View.GONE
            currentBitmap?.let {
                binding.photoEditorView.source.setImageBitmap(it)
            }

            Toast.makeText(this@PhotoEditorActivity, "Beauty effect applied!", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== AUTO ENHANCE ====================

    private fun autoEnhance() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            currentBitmap = withContext(Dispatchers.Default) {
                originalBitmap?.let {
                    ImageEnhancer.autoEnhance(it)
                }
            }

            binding.progressBar.visibility = View.GONE
            currentBitmap?.let {
                binding.photoEditorView.source.setImageBitmap(it)
            }

            Toast.makeText(this@PhotoEditorActivity, "Auto enhanced!", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== RESET & COMPARE ====================

    private fun resetImage() {
        brightness = 0f
        contrast = 1f
        saturation = 1f
        sharpness = 0f
        exposure = 0f
        highlights = 0.5f
        shadows = 0.5f
        temperature = 5000f
        tint = 0f
        hue = 0f
        vignetteIntensity = 0f
        currentFilter = "None"

        currentBitmap = originalBitmap?.copy(
            originalBitmap!!.config ?: Bitmap.Config.ARGB_8888,
            true
        )
        binding.photoEditorView.source.setImageBitmap(currentBitmap)

        Toast.makeText(this, "Image reset", Toast.LENGTH_SHORT).show()
    }

    private var isShowingOriginal = false

    private fun compareBeforeAfter() {
        isShowingOriginal = !isShowingOriginal
        binding.photoEditorView.source.setImageBitmap(
            if (isShowingOriginal) originalBitmap else currentBitmap
        )
        binding.btnCompare.contentDescription = if (isShowingOriginal) "After" else "Before"
    }

    // ==================== SAVE & SHARE ====================

    private fun saveImage() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            val saved = withContext(Dispatchers.IO) {
                try {
                    val fileName = "luminex_${System.currentTimeMillis()}.jpg"
                    val file = File(getExternalFilesDir(null), fileName)

                    currentBitmap?.let {
                        ImageEnhancer.saveBitmap(it, file, quality = 95)
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            binding.progressBar.visibility = View.GONE

            if (saved) {
                savePhotoToDatabase()
                Toast.makeText(this@PhotoEditorActivity, "Photo saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@PhotoEditorActivity, "Failed to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun savePhotoToDatabase() {
        withContext(Dispatchers.IO) {
            try {
                val user = database.userDao().getCurrentUserSync()
                user?.let {
                    val photo = Photo(
                        userId = it.userId,
                        originalPath = imagePath,
                        editedPath = "",
                        fileName = "luminex_${System.currentTimeMillis()}.jpg",
                        filterApplied = currentFilter,
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation
                    )

                    database.photoDao().insertPhoto(photo)
                    database.userDao().incrementUserStats(it.userId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun shareImage() {
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val tempFile = File(cacheDir, "share_${System.currentTimeMillis()}.jpg")
                    currentBitmap?.let {
                        ImageEnhancer.saveBitmap(it, tempFile)
                        tempFile
                    }
                }

                file?.let {
                    val uri = FileProvider.getUriForFile(
                        this@PhotoEditorActivity,
                        "${packageName}.fileprovider",
                        it
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    startActivity(Intent.createChooser(shareIntent, "Share Photo"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@PhotoEditorActivity, "Failed to share", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== EXIT HANDLING ====================

    private fun showExitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Changes?")
            .setMessage("Your edits will be lost if you leave without saving.")
            .setPositiveButton("Discard") { _, _ ->
                finish()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Save") { _, _ ->
                saveImage()
                finish()
            }
            .show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        showExitConfirmation()
    }

    override fun onDestroy() {
        super.onDestroy()
        ImageEnhancer.recycleBitmap(originalBitmap)
        ImageEnhancer.recycleBitmap(currentBitmap)
    }
}