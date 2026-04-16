package com.example.pulserds_prog7313.ui

import android.app.DatePickerDialog
import android.view.View
import android.widget.*
import java.util.*
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R

class AddTransactionActivity : AppCompatActivity() {

    private var isExpense = true
    private var selectedCategory = 1

    private lateinit var amountInput: EditText
    private lateinit var merchantInput: EditText
    private lateinit var accountDropdown: AutoCompleteTextView
    private lateinit var dateInput: EditText
    private lateinit var noteInput: EditText

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
        setupClicks()
        setupDatePicker()
    }

    private fun initViews() {
        amountInput = findViewById(R.id.amountInput)
        merchantInput = findViewById(R.id.merchantEditText)
        accountDropdown = findViewById(R.id.accountDropdown)
        dateInput = findViewById(R.id.dateEditText)
        noteInput = findViewById(R.id.noteEditText)

    }

    private fun setupClicks() {

        //back button
        findViewById<TextView>(R.id.backArrow).setOnClickListener {
            finish()
        }

        //cancel button
        findViewById<TextView>(R.id.cancelButton).setOnClickListener {
            finish()
        }

        //expense toggle
        findViewById<TextView>(R.id.expenseToggle).setOnClickListener {
            isExpense = true
        }

        //income toggle
        findViewById<TextView>(R.id.incomeToggle).setOnClickListener {
            isExpense = false
        }

        setupCategorySelection()
        setupAccountDropdown()
    }

    private fun setupCategorySelection() {

        val categories = listOf(
            R.id.catGroceries,
            R.id.catDining,
            R.id.catTransport,
            R.id.catEntertainment,
            R.id.catSubscriptions,
            R.id.catUtilities,
            R.id.catIncome,
            R.id.catInvest
        )

        categories.forEachIndexed { index, id ->
            findViewById<View>(id).setOnClickListener {
                selectedCategory = index + 1
            }
        }
    }

    private fun setupAccountDropdown() {

        val accounts = listOf(
            "Cash",
            "Bank Account",
            "Credit Card",
            "Savings"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            accounts
        )

        accountDropdown.setAdapter(adapter)
    }

    private fun setupDatePicker() {

        dateInput.setOnClickListener {

            val calender = Calendar.getInstance()

            DatePickerDialog(
                this,
                {_, year, month, day ->

                    val selected =
                        String.format("%02d/%02d/%04d", day, month + 1, year)

                    dateInput.setText(selected)
                },
                calender.get(Calendar.YEAR),
                calender.get(Calendar.MONTH),
                calender.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }
}