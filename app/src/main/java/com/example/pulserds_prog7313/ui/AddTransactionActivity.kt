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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.repository.ExpenseEntryRepository
import com.example.pulserds_prog7313.utils.CurrencyManager
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ExpenseEntryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.io.File
import java.util.*

//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
// [Accessed: 28 Apr. 2026].
//[3] Google, "LiveData overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/livedata.
// [Accessed: 28 Apr. 2026].
//[4] JetBrains, "Coroutines guide," Kotlin Documentation, 2024. [Online].
// Available: https://kotlinlang.org/docs/coroutines-guide.html.
// [Accessed: 28 Apr. 2026].
//[8] JetBrains, kotlinx-coroutines-android, ver. 1.8.1. [Software]. JetBrains, 2024.
// Available: https://github.com/Kotlin/kotlinx.coroutines.
//[9] Google, AndroidX Core KTX, ver. 1.13.1. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/core.
//[10] Google, AndroidX AppCompat, ver. 1.7.0. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/appcompat.
//[11] Google, Material Design Components for Android, ver. 1.12.0. [Software]. Google, 2024.
// Available: https://github.com/material-components/material-components-android.
//[13] Google, AndroidX Activity KTX, ver. 1.9.0. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/activity.
//[15] Google, "View Binding," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/view-binding.
// [Accessed: 28 Apr. 2026].
//[16] Google, "Requesting permissions at runtime," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/permissions/requesting.
// [Accessed: 28 Apr. 2026].
//[17] Google, "Share files with FileProvider," Android Developers, 2024. [Online].
// Available: https://developer.android.com/reference/androidx/core/content/FileProvider.
// [Accessed: 28 Apr. 2026].
//[25] 	F. van Puffelen, "Save a Room Database file in Firebase," Stack Exchange Inc, 22 August 2021. [Online].
// Available: https://stackoverflow.com/questions/68878022/save-a-room-database-file-in-firebase.
// [Accessed 30 May 2026].
//[26] 	A. Mamo, "Android Room and Firebase in a MVVM Architecture - Offline First Application," Stack Exchange Inc, 18 November 2022. [Online].
// Available: https://stackoverflow.com/questions/74484239/android-room-and-firebase-in-a-mvvm-architecture-offline-first-application.
// [Accessed 30 May 2026].

class AddTransactionActivity : AppCompatActivity() {

    private var isExpense = true
    private var selectedCategoryId: String? = null   // String ID
    private var uriOfCameraImage: Uri? = null
    private var uriOfSelectedReceipt: Uri? = null
    private lateinit var viewModel: ExpenseEntryViewModel
    private lateinit var bankAccountViewModel: BankAccountViewModel
    private lateinit var categoryViewModel: CategoryViewModel
    private var loggedInUserId: String = ""           // String UID
    private var accountsList: List<BankAccount> = emptyList()
    private var selectedAccountId: String? = null     // String ID
    private var categoriesList = mutableListOf<Category>()

    private lateinit var amountInput: EditText
    private lateinit var merchantInput: EditText
    private lateinit var accountDropdown: AutoCompleteTextView
    private lateinit var categoryDropdown: AutoCompleteTextView
    private lateinit var dateInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var expenseToggle: TextView
    private lateinit var incomeToggle: TextView

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriOfCameraImage != null) handleSelectedReceipt(uriOfCameraImage!!)
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleSelectedReceipt(it) }
    }
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleSelectedReceipt(it) }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openPhoneCamera()
        else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_transaction)

        // userId is now a String -- get from SessionManager (String)
        loggedInUserId = SessionManager(this).getUserId()

        // No AppDatabase -- repositories use Firebase directly
        viewModel = ViewModelProvider(this, ExpenseEntryViewModelFactory(ExpenseEntryRepository()))[ExpenseEntryViewModel::class.java]

        bankAccountViewModel = ViewModelProvider(this, BankAccountViewModelFactory(BankAccountRepository()))[BankAccountViewModel::class.java]
        bankAccountViewModel.init(loggedInUserId)

        val catFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(CategoryRepository()) as T
            }
        }
        categoryViewModel = ViewModelProvider(this, catFactory)[CategoryViewModel::class.java]

        if (loggedInUserId.isNotBlank()) {
            lifecycleScope.launch { categoryViewModel.seedDefaultCategories(loggedInUserId) }
        }

        findViewById<View>(R.id.receiptEmptyState).setOnClickListener { openPhoneGallery() }
        findViewById<View>(R.id.receiptCameraBtn).setOnClickListener { checkCameraPermission() }
        findViewById<View>(R.id.receiptGalleryBtn).setOnClickListener { openPhoneGallery() }
        findViewById<View>(R.id.receiptFilesBtn).setOnClickListener { openPhoneFilesExplorer() }
        findViewById<View>(R.id.receiptViewBtn).setOnClickListener { uriOfSelectedReceipt?.let { viewReceipt(it) } }
        findViewById<View>(R.id.receiptRemoveBtn).setOnClickListener { removeReceipt() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        initViews()
        refreshCurrencySymbol()
        setupToggle()
        setupCategoryDropdown()
        setupAccountDropdown()
        setupDatePicker()
        setupNavigation()
        setupSaveButton()
    }

    override fun onResume() {
        super.onResume()
        refreshCurrencySymbol()
        if (loggedInUserId.isNotBlank()) {
            lifecycleScope.launch { categoryViewModel.seedDefaultCategories(loggedInUserId) }
        }
    }

    private fun refreshCurrencySymbol() {
        findViewById<TextView>(R.id.currencySymbolLabel)?.text =
            CurrencyManager.getSelectedCurrency(this).symbol
    }

    private fun initViews() {
        amountInput      = findViewById(R.id.amountInput)
        merchantInput    = findViewById(R.id.merchantEditText)
        accountDropdown  = findViewById(R.id.accountDropdown)
        categoryDropdown = findViewById(R.id.categoryDropdown)
        dateInput        = findViewById(R.id.dateEditText)
        noteInput        = findViewById(R.id.noteEditText)
        expenseToggle    = findViewById(R.id.expenseToggle)
        incomeToggle     = findViewById(R.id.incomeToggle)
    }

    private fun setupToggle() {
        updateToggleUI()
        expenseToggle.setOnClickListener { isExpense = true; updateToggleUI(); refreshCategoryDropdown() }
        incomeToggle.setOnClickListener  { isExpense = false; updateToggleUI(); refreshCategoryDropdown() }
    }

    private fun updateToggleUI() {
        expenseToggle.alpha = if (isExpense) 1f else 0.45f
        incomeToggle.alpha  = if (isExpense) 0.45f else 1f
    }

    private fun setupCategoryDropdown() {
        categoryViewModel.getCategoriesLive(loggedInUserId).observe(this) { all ->
            if (all != null) {
                categoriesList.clear()

                val defaultCategories = setOf(
                    "groceries", "dining out", "transport", "entertainment",
                    "subscriptions", "utilities", "healthcare", "shopping",
                    "salary", "investment", "freelance", "gift"
                )

                val fixedCategories = all.map { category ->
                    val cleanName = category.name.lowercase().trim()
                    if (defaultCategories.contains(cleanName)) {
                        category.copy(isDefault = true)
                    } else {
                        category
                    }
                }

                val differentCategories = fixedCategories.distinctBy { it.name.lowercase().trim() }
                categoriesList.addAll(differentCategories)
                refreshCategoryDropdown()
            }
        }
        categoryDropdown.setOnClickListener { categoryDropdown.showDropDown() }
        categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = filteredCategories()[position].id
        }
    }

    private fun filteredCategories(): List<Category> {
        val typeNeeded = if (isExpense) "EXPENSE" else "INCOME"
        return categoriesList.filter { it.type == typeNeeded || it.type == "BOTH" }
    }

    private fun refreshCategoryDropdown() {
        val filtered = filteredCategories()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filtered.map { it.name })
        categoryDropdown.setAdapter(adapter)
        categoryDropdown.setText("", false)
        selectedCategoryId = null
    }

    private fun setupAccountDropdown() {
        bankAccountViewModel.accounts.observe(this) { accounts ->
            accountsList = accounts
            val labels = accounts.map { "${it.nickname} (${it.bankName})" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
            accountDropdown.setAdapter(adapter)
            val primaryIndex = accounts.indexOfFirst { it.isPrimary }
            if (primaryIndex >= 0) {
                accountDropdown.setText(labels[primaryIndex], false)
                selectedAccountId = accounts[primaryIndex].id
            }
            accountDropdown.setOnItemClickListener { _, _, position, _ ->
                selectedAccountId = accounts[position].id
            }
        }
        accountDropdown.setOnClickListener { accountDropdown.showDropDown() }
    }

    private var datePickerDialog: DatePickerDialog? = null

    private fun setupDatePicker() {
        val openPicker = View.OnClickListener {
            val cal = Calendar.getInstance()
            datePickerDialog = DatePickerDialog(this, { _, year, month, day ->
                dateInput.setText(String.format("%02d/%02d/%04d", day, month + 1, year))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            datePickerDialog?.show()
        }
        dateInput.setOnClickListener(openPicker)
    }

    override fun onDestroy() { super.onDestroy(); datePickerDialog?.dismiss() }

    private fun setupNavigation() {
        findViewById<TextView>(R.id.backArrow)?.setOnClickListener { finish() }
        findViewById<TextView>(R.id.manageCategoriesLink)?.setOnClickListener {
            startActivity(Intent(this, ManageCategoriesActivity::class.java))
        }
    }

    private fun setupSaveButton() {
        findViewById<View>(R.id.saveTransactionButton).setOnClickListener { saveTransaction() }
        findViewById<View>(R.id.cancelButton).setOnClickListener { finish() }
    }

    private fun openPhoneGallery()       { galleryLauncher.launch("image/*") }
    private fun openPhoneFilesExplorer() { fileLauncher.launch("*/*") }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            openPhoneCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openPhoneCamera() {
        val photo = createImageFile()
        uriOfCameraImage = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photo)
        uriOfCameraImage?.let { cameraLauncher.launch(it) }
    }

    private fun createImageFile(): File {
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("RECEIPT_${ts}_", ".jpg", dir)
    }

    private fun handleSelectedReceipt(uri: Uri) {
        val mimeType = contentResolver.getType(uri)
        if (mimeType !in listOf("image/jpeg", "image/png", "application/pdf")) {
            Toast.makeText(this, "Only JPG, PNG or PDF files allowed", Toast.LENGTH_SHORT).show(); return
        }
        val size = getSizeOfFile(uri)
        if (size > 10 * 1024 * 1024L) {
            Toast.makeText(this, "File exceeds the 10 MB limit", Toast.LENGTH_SHORT).show(); return
        }

        // Copy the file into the app's private receipts directory immediately.
        // content://media/picker_get_content/... URIs from the photo picker are
        // TEMPORARY - they expire when the app is closed. Saving the raw URI to
        // Firestore and opening it later causes a SecurityException which
        // crashes TransactionsActivity. Copying to private storage gives a
        // permanent FileProvider URI that is always readable by the app.
        val permanentUri = copyReceiptToPrivateStorage(uri, mimeType)
        if (permanentUri == null) {
            Toast.makeText(this, "Could not attach receipt. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        uriOfSelectedReceipt = permanentUri
        showAttachedReceiptState(permanentUri, mimeType, size)
    }

    /**
     * Copies the content at [sourceUri] into the app's private receipts folder
     * (filesDir/receipts/) and returns a FileProvider URI for the copy, or null
     * if the copy fails.
     *
     * A FileProvider URI (content://...fileprovider/...) is used instead of a
     * raw file:// URI to avoid FileUriExposedException on Android 7+ while
     * still letting viewer apps receive a read-permission grant.
     */
    private fun copyReceiptToPrivateStorage(sourceUri: Uri, mimeType: String?): Uri? {
        return try {
            val ext = when (mimeType) {
                "application/pdf" -> ".pdf"
                "image/png"       -> ".png"
                else              -> ".jpg"
            }
            val dir  = File(filesDir, "receipts").also { it.mkdirs() }
            val dest = File(dir, "receipt_${System.currentTimeMillis()}$ext")

            contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }

            FileProvider.getUriForFile(this, "${packageName}.fileprovider", dest)
        } catch (e: Exception) {
            null
        }
    }

    private fun getSizeOfFile(uri: Uri) =
        contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L

    private fun showAttachedReceiptState(uri: Uri, mimeType: String?, size: Long) {
        findViewById<View>(R.id.receiptEmptyState).visibility    = View.GONE
        findViewById<View>(R.id.receiptAttachedState).visibility = View.VISIBLE
        findViewById<TextView>(R.id.receiptFileName).text        = getFileName(uri)
        val sizeStr = formatSizeOfFile(size)
        val typeStr = if (mimeType == "application/pdf") "PDF" else "JPG"
        findViewById<TextView>(R.id.receiptFileSize).text = "$sizeStr $typeStr"
        val thumbnail = findViewById<ImageView>(R.id.receiptThumbnail)
        val pdfIcon   = findViewById<TextView>(R.id.receiptPdfIcon)
        if (mimeType == "application/pdf") { thumbnail.visibility = View.GONE; pdfIcon.visibility = View.VISIBLE }
        else { thumbnail.visibility = View.VISIBLE; pdfIcon.visibility = View.GONE; thumbnail.setImageURI(uri) }
    }

    private fun getFileName(uri: Uri): String {
        var name = "receipt_file"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) name = c.getString(idx)
        }
        return name
    }

    private fun formatSizeOfFile(bytes: Long) =
        if (bytes >= 1024 * 1024) String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else String.format("%.0f KB", bytes / 1024.0)

    private fun viewReceipt(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open receipt with"))
    }

    private fun removeReceipt() {
        uriOfSelectedReceipt = null; uriOfCameraImage = null
        findViewById<View>(R.id.receiptEmptyState).visibility    = View.VISIBLE
        findViewById<View>(R.id.receiptAttachedState).visibility = View.GONE
    }

    private fun saveTransaction() {
        val amountStr   = amountInput.text.toString().trim()
        val description = merchantInput.text.toString().trim()
        val date        = dateInput.text.toString().trim()
        val note        = noteInput.text.toString().trim()
        val accountText = accountDropdown.text.toString().trim()

        if (amountStr.isEmpty())   { amountInput.error = "Please enter an amount"; amountInput.requestFocus(); return }
        val rawAmount = amountStr.toDoubleOrNull()
        if (rawAmount == null || rawAmount <= 0) { amountInput.error = "Please enter a valid amount"; amountInput.requestFocus(); return }
        if (description.isEmpty()) { merchantInput.error = "Please enter a description"; merchantInput.requestFocus(); return }
        if (date.isEmpty())        { dateInput.error = "Please select a date"; dateInput.requestFocus(); return }
        if (accountText.isEmpty()) { accountDropdown.error = "Please select an account"; accountDropdown.requestFocus(); return }
        if (selectedAccountId == null) { accountDropdown.error = "Please select a valid account"; accountDropdown.requestFocus(); return }
        if (selectedCategoryId == null) { categoryDropdown.error = "Please select a category"; categoryDropdown.requestFocus(); return }
        if (loggedInUserId.isBlank()) { Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show(); return }

        val finalAmount = if (isExpense) -abs(rawAmount) else abs(rawAmount)
        val fullDescription = buildString {
            append(description)
            if (note.isNotEmpty()) { append("\n"); append(note) }
        }

        val entry = ExpenseEntry(
            userId        = loggedInUserId,
            categoryId    = selectedCategoryId!!,
            bankAccountId = selectedAccountId!!,
            amount        = finalAmount,
            date          = date,
            startTime     = "",
            endTime       = "",
            description   = fullDescription,
            photoPath     = uriOfSelectedReceipt?.toString()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repo     = ExpenseEntryRepository()
                val bankRepo = BankAccountRepository()
                repo.addEntry(entry)
                bankRepo.adjustBalance(selectedAccountId!!, finalAmount)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@AddTransactionActivity, if (isExpense) "Expense saved!" else "Income saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@AddTransactionActivity, "Failed to save transaction. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

// ViewModel factories (kept local to this file, same as before)
class ExpenseEntryViewModelFactory(private val repository: ExpenseEntryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return ExpenseEntryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class BankAccountViewModelFactory(private val repository: BankAccountRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BankAccountViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return BankAccountViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}