package com.example.pulserds_prog7313.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    //State
    private var isExpense = true
    private var selectedCategoryId = 1          // DB id of the chosen category
    private var selectedCategoryViewId: Int? = null  // View id of the highlighted tile

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_transaction)

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
}