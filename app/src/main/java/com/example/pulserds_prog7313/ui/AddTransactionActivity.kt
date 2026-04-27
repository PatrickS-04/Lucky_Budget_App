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
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.repository.ExpenseEntryRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ExpenseEntryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.io.File
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    // State
    private var isExpense = true
    private var selectedCategoryId: Int? = null
    private var uriOfCameraImage: Uri? = null
    private var uriOfSelectedReceipt: Uri? = null
    private lateinit var viewModel: ExpenseEntryViewModel
    private lateinit var bankAccountViewModel: BankAccountViewModel
    private lateinit var categoryViewModel: CategoryViewModel
    private var loggedInUserId: Int = -1
    private var accountsList: List<BankAccount> = emptyList()
    private var selectedAccountId: Int? = null
    private var categoriesList: List<Category> = emptyList()

    // View references
    private lateinit var amountInput: EditText
    private lateinit var merchantInput: EditText
    private lateinit var accountDropdown: AutoCompleteTextView
    private lateinit var categoryDropdown: AutoCompleteTextView
    private lateinit var dateInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var expenseToggle: TextView
    private lateinit var incomeToggle: TextView

    // Activity result launchers
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

    // Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_transaction)

        loggedInUserId = intent.getIntExtra("USER_ID", -1)
        if (loggedInUserId == -1) loggedInUserId = SessionManager(this).getUserId()

        val db = AppDatabase.getDatabase(applicationContext)

        // ExpenseEntry ViewModel
        val expenseRepo = ExpenseEntryRepository(db.expenseEntryDao())
        viewModel = ViewModelProvider(this, ExpenseEntryViewModelFactory(expenseRepo))[ExpenseEntryViewModel::class.java]

        // BankAccount ViewModel
        val bankRepo = BankAccountRepository(db.bankAccountDao())
        bankAccountViewModel = ViewModelProvider(this, BankAccountViewModelFactory(bankRepo))[BankAccountViewModel::class.java]
        bankAccountViewModel.init(loggedInUserId)

        // Category ViewModel
        val catRepo = CategoryRepository(db.categoryDao())
        val catFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(catRepo) as T
            }
        }
        categoryViewModel = ViewModelProvider(this, catFactory)[CategoryViewModel::class.java]

        // FIX: seedDefaultCategories is suspend — must be called inside a coroutine
        if (loggedInUserId != -1) {
            lifecycleScope.launch {
                categoryViewModel.seedDefaultCategories(loggedInUserId)
            }
        }

        // Receipt buttons
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
        setupToggle()
        setupCategoryDropdown()
        setupAccountDropdown()
        setupDatePicker()
        setupNavigation()
        setupSaveButton()
    }

    override fun onResume() {
        super.onResume()
        // FIX: seedDefaultCategories is suspend — must be called inside a coroutine
        if (loggedInUserId != -1) {
            lifecycleScope.launch {
                categoryViewModel.seedDefaultCategories(loggedInUserId)
            }
        }
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

    // Toggle Expense / Income
    private fun setupToggle() {
        updateToggleUI()
        expenseToggle.setOnClickListener { isExpense = true; updateToggleUI(); refreshCategoryDropdown() }
        incomeToggle.setOnClickListener  { isExpense = false; updateToggleUI(); refreshCategoryDropdown() }
    }

    private fun updateToggleUI() {
        expenseToggle.alpha = if (isExpense) 1f else 0.45f
        incomeToggle.alpha  = if (isExpense) 0.45f else 1f
    }

    // Category dropdown
    private fun setupCategoryDropdown() {
        categoryViewModel.getCategoriesLive(loggedInUserId).observe(this) { all ->
            categoriesList = all
            refreshCategoryDropdown()
        }
        categoryDropdown.setOnClickListener { categoryDropdown.showDropDown() }
        categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            val filtered = filteredCategories()
            selectedCategoryId = filtered[position].id
        }
    }

    private fun filteredCategories(): List<Category> {
        val typeNeeded = if (isExpense) "EXPENSE" else "INCOME"
        return categoriesList.filter { it.type == typeNeeded || it.type == "BOTH" }
    }

    private fun refreshCategoryDropdown() {
        val filtered = filteredCategories()
        val labels = filtered.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        categoryDropdown.setAdapter(adapter)
        categoryDropdown.setText("", false)
        selectedCategoryId = null
    }

    // Account dropdown
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

    // Date picker
    private fun setupDatePicker() {
        val openPicker = View.OnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    dateInput.setText(String.format("%02d/%02d/%04d", day, month + 1, year))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        dateInput.setOnClickListener(openPicker)
    }

    // Navigation
    private fun setupNavigation() {
        findViewById<TextView>(R.id.backArrow)?.setOnClickListener { finish() }
        findViewById<TextView>(R.id.manageCategoriesLink)?.setOnClickListener {
            val intent = Intent(this, ManageCategoriesActivity::class.java)
            intent.putExtra("USER_ID", loggedInUserId)
            startActivity(intent)
        }
    }

    private fun setupSaveButton() {
        findViewById<View>(R.id.saveTransactionButton).setOnClickListener { saveTransaction() }
        findViewById<View>(R.id.cancelButton).setOnClickListener { finish() }
    }

    // Receipt helpers
    private fun openPhoneGallery()       { galleryLauncher.launch("image/*") }
    private fun openPhoneFilesExplorer() { fileLauncher.launch("*/*") }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            openPhoneCamera()
        else
            permissionLauncher.launch(Manifest.permission.CAMERA)
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
            Toast.makeText(this, "Only JPG, PNG or PDF files allowed", Toast.LENGTH_SHORT).show()
            return
        }
        val size = getSizeOfFile(uri)
        if (size > 10 * 1024 * 1024L) {
            Toast.makeText(this, "File exceeds the 10 MB limit", Toast.LENGTH_SHORT).show()
            return
        }
        uriOfSelectedReceipt = uri
        showAttachedReceiptState(uri, mimeType, size)
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
        if (mimeType == "application/pdf") {
            thumbnail.visibility = View.GONE
            pdfIcon.visibility   = View.VISIBLE
        } else {
            thumbnail.visibility = View.VISIBLE
            pdfIcon.visibility   = View.GONE
            thumbnail.setImageURI(uri)
        }
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
        uriOfSelectedReceipt = null
        uriOfCameraImage     = null
        findViewById<View>(R.id.receiptEmptyState).visibility    = View.VISIBLE
        findViewById<View>(R.id.receiptAttachedState).visibility = View.GONE
    }

    // Save transaction
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
        if (loggedInUserId == -1) { Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show(); return }

        val finalAmount = if (isExpense) -abs(rawAmount) else abs(rawAmount)

        val fullDescription = buildString {
            append(description)
            if (note.isNotEmpty()) { append("\n"); append(note) }
        }

        val accountId = selectedAccountId!!

        val entry = ExpenseEntry(
            userId        = loggedInUserId,
            categoryId    = selectedCategoryId!!,
            bankAccountId = accountId,
            amount        = finalAmount,
            date          = date,
            startTime     = "",
            endTime       = "",
            description   = fullDescription,
            photoPath     = uriOfSelectedReceipt?.toString()
        )

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.expenseEntryDao().insert(entry)
            db.bankAccountDao().adjustBalance(accountId, finalAmount)
        }

        Toast.makeText(this, if (isExpense) "Expense saved!" else "Income saved!", Toast.LENGTH_SHORT).show()
        finish()
    }
}

class ExpenseEntryViewModelFactory(private val repository: ExpenseEntryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseEntryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class BankAccountViewModelFactory(private val repository: BankAccountRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BankAccountViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BankAccountViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}