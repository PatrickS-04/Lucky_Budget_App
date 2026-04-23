package com.example.pulserds_prog7313.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R
import java.io.File
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    //State
    private var isExpense = true
    private var selectedCategoryId = 1          // DB id of the chosen category
    private var selectedCategoryViewId: Int? = null  // View id of the highlighted tile
    private var uriOfCameraImage: Uri? = null
    private var uriOfSelectedReceipt: Uri? = null

    //View references
    private lateinit var amountInput: EditText
    private lateinit var merchantInput: EditText
    private lateinit var accountDropdown: AutoCompleteTextView
    private lateinit var dateInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var expenseToggle: TextView
    private lateinit var incomeToggle: TextView

    //Category tile ids in order matches DB seed / category list
    private val categoryViewIds = listOf(
        R.id.catGroceries,
        R.id.catDining,
        R.id.catTransport,
        R.id.catEntertainment,
        R.id.catSubscriptions,
        R.id.catUtilities,
        R.id.catIncome,
        R.id.catInvest
    )

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && uriOfCameraImage != null)
        {
            handleSelectedReceipt(uriOfCameraImage!!)
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleSelectedReceipt(it) }
    }

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleSelectedReceipt(it) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openPhoneCamera()
        }
        else {
            Toast.makeText(this, "Permission for camera denied", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_transaction)

        findViewById<View>(R.id.receiptEmptyState).setOnClickListener {
            openPhoneGallery()
        }

        findViewById<View>(R.id.receiptCameraBtn).setOnClickListener {
            checkCameraPermission()
        }

        findViewById<View>(R.id.receiptGalleryBtn).setOnClickListener {
            openPhoneGallery()
        }

        findViewById<View>(R.id.receiptFilesBtn).setOnClickListener {
            openPhoneFilesExplorer()
        }

        findViewById<View>(R.id.receiptViewBtn).setOnClickListener {
            uriOfSelectedReceipt?.let {
                viewReceipt(it)
            }
        }

        findViewById<View>(R.id.receiptRemoveBtn).setOnClickListener {
            removeReceipt()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupToggle()
        setupCategorySelection()
        setupAccountDropdown()
        setupDatePicker()
        setupNavigation()
    }

    private fun initViews() {
        amountInput     = findViewById(R.id.amountInput)
        merchantInput   = findViewById(R.id.merchantEditText)
        accountDropdown = findViewById(R.id.accountDropdown)
        dateInput       = findViewById(R.id.dateEditText)
        noteInput       = findViewById(R.id.noteEditText)
        expenseToggle   = findViewById(R.id.expenseToggle)
        incomeToggle    = findViewById(R.id.incomeToggle)
    }

    //Toggle Expense/Income

    private fun setupToggle() {
        updateToggleUI()   // reflect default state

        expenseToggle.setOnClickListener {
            isExpense = true
            updateToggleUI()
        }

        incomeToggle.setOnClickListener {
            isExpense = false
            updateToggleUI()
        }
    }

    /**
     * Visually highlight the active toggle.
     * The active pill gets an opaque background; the inactive one is translucent.
     */
    private fun updateToggleUI() {
        if (isExpense) {
            expenseToggle.alpha = 1f
            incomeToggle.alpha  = 0.45f
        } else {
            incomeToggle.alpha  = 1f
            expenseToggle.alpha = 0.45f
        }
    }

    //Category selection

    private fun setupCategorySelection() {
        categoryViewIds.forEachIndexed { index, viewId ->
            val tile = findViewById<View>(viewId) ?: return@forEachIndexed
            tile.setOnClickListener {
                selectedCategoryId     = index + 1
                selectedCategoryViewId = viewId
                highlightSelectedCategory()
            }
        }
        // Pre-select the first tile so the form always has a valid category.
        selectedCategoryViewId = categoryViewIds.firstOrNull()
        highlightSelectedCategory()
    }

    /**
     * Adds a coloured border / raised elevation to the chosen category tile
     * and resets all others to their default appearance.
     */
    private fun highlightSelectedCategory() {
        categoryViewIds.forEach { viewId ->
            val tile = findViewById<View>(viewId) ?: return@forEach
            if (viewId == selectedCategoryViewId) {
                tile.alpha     = 1f
                tile.scaleX    = 1.08f
                tile.scaleY    = 1.08f
                tile.elevation = 8f
            } else {
                tile.alpha     = 0.55f
                tile.scaleX    = 1f
                tile.scaleY    = 1f
                tile.elevation = 0f
            }
        }
    }

    //Account dropdown

    private fun setupAccountDropdown() {
        val accounts = listOf("Cash", "Bank Account", "Credit Card", "Savings")
        val adapter  = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, accounts)
        accountDropdown.setAdapter(adapter)
        accountDropdown.setOnClickListener { accountDropdown.showDropDown() }
    }

    //Date picker

    private fun setupDatePicker() {
        val openPicker = View.OnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    dateInput.setText(
                        String.format("%02d/%02d/%04d", day, month + 1, year)
                    )
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dateInput.setOnClickListener(openPicker)
    }

    //Navigation

    private fun setupNavigation() {
        findViewById<TextView>(R.id.backArrow)?.setOnClickListener { finish() }
    }

    // Opens the Phones Gallery
    private fun openPhoneGallery() {
        galleryLauncher.launch("image/*")
    }

    // Checks if there is permission for the phone to use the camera
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        {
            openPhoneCamera()
        }
        else
        {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Opens Phone Camera
    private fun openPhoneCamera() {
        val photo = createImageFile()
        uriOfCameraImage = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photo
        )
        uriOfCameraImage?.let { cameraLauncher.launch(it) }
    }

    // Creates a file for the camera to use
    private fun createImageFile(): File {
        val imageTimeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val directoryOfStorage = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("RECEIPT_${imageTimeStamp}_", ".jpg", directoryOfStorage)
    }

    // Opens the Phones Files Explorer
    private fun openPhoneFilesExplorer() {
        fileLauncher.launch("*/*")
    }

    // Handles the selected receipt
    private fun handleSelectedReceipt(receiptUri: Uri) {
        val uriType = contentResolver.getType(receiptUri)

        val allowedReceipts = listOf("image/jpeg", "image/png", "application/pdf")

        if (uriType !in allowedReceipts) {
            Toast.makeText(this, "Only JPG, PNG or PDFs are allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val maximumBytes = 1024 * 1024L * 10
        val sizeOfFile = getSizeOfFile(receiptUri)

        if (maximumBytes < sizeOfFile)
        {
            Toast.makeText(this, "File exceeds the 10MB limit", Toast.LENGTH_SHORT).show()
            return
        }

        uriOfSelectedReceipt = receiptUri
        showAttachedReceiptState(receiptUri, uriType, sizeOfFile)
    }

    // Gets the size of the file
    private fun getSizeOfFile(receiptUri: Uri): Long {
        return contentResolver.openFileDescriptor(receiptUri, "r")?.use {
            it.statSize
        } ?: 0L
    }

    // Shows the attached receipt
    private fun showAttachedReceiptState(receiptUri: Uri, uriType: String?, sizeOfFile: Long) {
        findViewById<View>(R.id.receiptEmptyState).visibility = View.GONE
        findViewById<View>(R.id.receiptAttachedState).visibility = View.VISIBLE
        findViewById<TextView>(R.id.receiptFileName).text = getFileName(receiptUri)

        val size = formatSizeOfFile(sizeOfFile)
        val type = if (uriType == "application/pdf")
        {
            "PDF"
        }
        else
        {
            "JPG"
        }

        findViewById<TextView>(R.id.receiptFileSize).text = "$size $type"

        val thumbnail = findViewById<ImageView>(R.id.receiptThumbnail)
        val iconOfPdf = findViewById<TextView>(R.id.receiptPdfIcon)

        if (uriType == "application/pdf")
        {
            thumbnail.visibility = View.GONE
            iconOfPdf.visibility = View.VISIBLE
        }
        else
        {
            thumbnail.visibility = View.VISIBLE
            iconOfPdf.visibility = View.GONE
            thumbnail.setImageURI(receiptUri)
        }
    }

    // Gets the name of the file
    private fun getFileName(receiptUri: Uri): String {
        var fileName = "receipt_file"
        contentResolver.query(receiptUri, null, null, null, null)?.use { cursor ->
            val IndexOfName = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && IndexOfName >= 0)
            {
                fileName = cursor.getString(IndexOfName)
            }
        }
        return fileName
    }

    // Formats the size of the file
    private fun formatSizeOfFile(sizeOfFile: Long): String {
        return if (sizeOfFile >= 1024 * 1024)
        {
            String.format("%.1f MB", sizeOfFile / (1024.0 * 1024.0))
        }
        else
        {
            String.format("%.0f KB", sizeOfFile / 1024.0)
        }
    }

    // Allows the receipt to be seen by the user
    private fun viewReceipt(receiptUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(receiptUri, contentResolver.getType(receiptUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Open receipt with"))
    }

    // allows the user to remove their receipt
    private fun removeReceipt() {
        uriOfSelectedReceipt = null
        uriOfCameraImage = null

        findViewById<View>(R.id.receiptEmptyState).visibility = View.VISIBLE
        findViewById<View>(R.id.receiptAttachedState).visibility = View.GONE
    }
}