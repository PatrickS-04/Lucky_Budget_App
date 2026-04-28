package com.example.pulserds_prog7313.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AddAccountActivity : AppCompatActivity() {

    // -- ViewModel -------------------------------------------------------------
    private lateinit var viewModel: BankAccountViewModel
    private var userId = -1

    // -- Selection state -------------------------------------------------------
    private var selectedAccountType = ""
    private var selectedBank        = ""
    private var selectedColour      = "#4ADE80"   // default green

    // -- Account-type buttons --------------------------------------------------
    private lateinit var typeCheque  : LinearLayout
    private lateinit var typeSavings : LinearLayout
    private lateinit var typeCredit  : LinearLayout
    private lateinit var typeCrypto  : LinearLayout
    private lateinit var typeInvest  : LinearLayout
    private lateinit var typeWallet  : LinearLayout
    private val typeButtons get() = listOf(typeCheque, typeSavings, typeCredit,
        typeCrypto, typeInvest, typeWallet)
    private val typeLabels  = mapOf("typeCheque"  to "Cheque",
        "typeSavings" to "Savings",
        "typeCredit"  to "Credit",
        "typeCrypto"  to "Crypto",
        "typeInvest"  to "Invest",
        "typeWallet"  to "Wallet")

    // -- Bank buttons ----------------------------------------------------------
    private lateinit var bankStandard : LinearLayout
    private lateinit var bankFNB      : LinearLayout
    private lateinit var bankAbsa     : LinearLayout
    private lateinit var bankNedbank  : LinearLayout
    private lateinit var bankCapitec  : LinearLayout
    private lateinit var bankInvestec : LinearLayout
    private lateinit var bankBinance  : LinearLayout
    private lateinit var bankOther    : LinearLayout
    private val bankButtons get() = listOf(bankStandard, bankFNB, bankAbsa, bankNedbank,
        bankCapitec, bankInvestec, bankBinance, bankOther)

    // -- Input fields ----------------------------------------------------------
    private lateinit var nicknameLayout      : TextInputLayout
    private lateinit var nicknameEditText    : TextInputEditText
    private lateinit var accountNumberLayout : TextInputLayout
    private lateinit var accountNumberEditText: TextInputEditText
    private lateinit var balanceEditText     : TextInputEditText

    // -- Colour swatches -------------------------------------------------------
    private lateinit var colourGreen  : View
    private lateinit var colourBlue   : View
    private lateinit var colourYellow : View
    private lateinit var colourPurple : View
    private lateinit var colourRed    : View
    private lateinit var colourOrange : View
    private val colourMap get() = mapOf(
        colourGreen  to "#4ADE80",
        colourBlue   to "#60A5FA",
        colourYellow to "#FBBF24",
        colourPurple to "#C084FC",
        colourRed    to "#F87171",
        colourOrange to "#FB923C"
    )

    // -- Toggles ---------------------------------------------------------------
    private lateinit var includeInTotalSwitch  : Switch
    private lateinit var primaryAccountSwitch  : Switch

    // -- Preview views ---------------------------------------------------------
    private lateinit var previewDot     : View
    private lateinit var previewName    : TextView
    private lateinit var previewType    : TextView
    private lateinit var previewBalance : TextView

    // -- Buttons ---------------------------------------------------------------
    private lateinit var addAccountButton : MaterialButton
    private lateinit var cancelButton     : TextView
    private lateinit var backArrow        : TextView

    // -------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_account)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupViewModel()
        bindViews()
        setupTypeSelection()
        setupBankSelection()
        setupColourSelection()
        setupPreviewListeners()
        setupButtons()
        observeResults()
    }

    // -- ViewModel setup -------------------------------------------------------
    private fun setupViewModel() {
        userId = SessionManager(this).getUserId()
        val dao    = AppDatabase.getDatabase(this).bankAccountDao()
        val repo   = BankAccountRepository(dao)
        viewModel  = ViewModelProvider(this, ViewModelFactory { BankAccountViewModel(repo) })
            .get(BankAccountViewModel::class.java)
    }

    // -- View binding ----------------------------------------------------------
    private fun bindViews() {
        typeCheque  = findViewById(R.id.typeCheque)
        typeSavings = findViewById(R.id.typeSavings)
        typeCredit  = findViewById(R.id.typeCredit)
        typeCrypto  = findViewById(R.id.typeCrypto)
        typeInvest  = findViewById(R.id.typeInvest)
        typeWallet  = findViewById(R.id.typeWallet)

        bankStandard = findViewById(R.id.bankStandard)
        bankFNB      = findViewById(R.id.bankFNB)
        bankAbsa     = findViewById(R.id.bankAbsa)
        bankNedbank  = findViewById(R.id.bankNedbank)
        bankCapitec  = findViewById(R.id.bankCapitec)
        bankInvestec = findViewById(R.id.bankInvestec)
        bankBinance  = findViewById(R.id.bankBinance)
        bankOther    = findViewById(R.id.bankOther)

        nicknameLayout       = findViewById(R.id.nicknameLayout)
        nicknameEditText     = findViewById(R.id.nicknameEditText)
        accountNumberLayout  = findViewById(R.id.accountNumberLayout)
        accountNumberEditText= findViewById(R.id.accountNumberEditText)
        balanceEditText      = findViewById(R.id.balanceEditText)

        colourGreen  = findViewById(R.id.colourGreen)
        colourBlue   = findViewById(R.id.colourBlue)
        colourYellow = findViewById(R.id.colourYellow)
        colourPurple = findViewById(R.id.colourPurple)
        colourRed    = findViewById(R.id.colourRed)
        colourOrange = findViewById(R.id.colourOrange)

        includeInTotalSwitch = findViewById(R.id.includeInTotalSwitch)
        primaryAccountSwitch = findViewById(R.id.primaryAccountSwitch)

        previewDot     = findViewById(R.id.previewDot)
        previewName    = findViewById(R.id.previewName)
        previewType    = findViewById(R.id.previewType)
        previewBalance = findViewById(R.id.previewBalance)

        addAccountButton = findViewById(R.id.addAccountButton)
        cancelButton     = findViewById(R.id.cancelButton)
        backArrow        = findViewById(R.id.backArrow)
    }

    // -- Account-type tile selection -------------------------------------------
    private fun setupTypeSelection() {
        val tagMap = mapOf(
            typeCheque  to "Cheque",
            typeSavings to "Savings",
            typeCredit  to "Credit",
            typeCrypto  to "Crypto",
            typeInvest  to "Invest",
            typeWallet  to "Wallet"
        )
        tagMap.forEach { (btn, label) ->
            btn.setOnClickListener {
                selectedAccountType = label
                typeButtons.forEach { it.setBackgroundColor(0xFF1A2A38.toInt()) }
                btn.setBackgroundColor(0xFF0D2A1A.toInt())
                updatePreviewType()
            }
        }
        // Default selection: Cheque
        typeCheque.performClick()
    }

    // -- Bank tile selection ---------------------------------------------------
    private fun setupBankSelection() {
        val bankMap = mapOf(
            bankStandard to "Standard Bank",
            bankFNB      to "FNB",
            bankAbsa     to "Absa",
            bankNedbank  to "Nedbank",
            bankCapitec  to "Capitec",
            bankInvestec to "Investec",
            bankBinance  to "Binance",
            bankOther    to "Other"
        )
        bankMap.forEach { (btn, name) ->
            btn.setOnClickListener {
                selectedBank = name
                bankButtons.forEach { it.setBackgroundColor(0xFF1A2A38.toInt()) }
                btn.setBackgroundColor(0xFF0D2A1A.toInt())
                updatePreviewType()
            }
        }
        // Default selection: Standard Bank
        bankStandard.performClick()
    }

    // -- Colour swatch selection -----------------------------------------------
    private fun setupColourSelection() {
        colourMap.forEach { (view, hex) ->
            view.setOnClickListener {
                selectedColour = hex
                // Draw a white border on the selected swatch; reset others
                colourMap.keys.forEach { v -> v.alpha = 0.6f }
                view.alpha = 1f
                previewDot.setBackgroundColor(android.graphics.Color.parseColor(hex))
            }
        }
        colourGreen.alpha  = 1f   // default selected
    }

    // -- Live preview listeners ------------------------------------------------
    private fun setupPreviewListeners() {
        nicknameEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                previewName.text = if (s.isNullOrBlank()) "Account Nickname" else s.toString()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        balanceEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val amount = s?.toString()?.toDoubleOrNull() ?: 0.0
                previewBalance.text = "R %.2f".format(amount)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun updatePreviewType() {
        val type = selectedAccountType.ifBlank { "Type" }
        val bank = selectedBank.ifBlank { "Bank" }
        previewType.text = "$type  •  $bank"
    }

    // -- Button listeners ------------------------------------------------------
    private fun setupButtons() {
        backArrow.setOnClickListener    { finish() }
        cancelButton.setOnClickListener { finish() }

        addAccountButton.setOnClickListener {
            // Clear previous errors
            nicknameLayout.error      = null
            accountNumberLayout.error = null

            val nickname      = nicknameEditText.text?.toString() ?: ""
            val accountNumber = accountNumberEditText.text?.toString() ?: ""
            val balance       = balanceEditText.text?.toString()?.toDoubleOrNull() ?: 0.0

            viewModel.addAccount(
                userId         = userId,
                accountType    = selectedAccountType,
                bankName       = selectedBank,
                nickname       = nickname,
                accountNumber  = accountNumber,
                balance        = balance,
                colour         = selectedColour,
                includeInTotal = includeInTotalSwitch.isChecked,
                setPrimary     = primaryAccountSwitch.isChecked
            )
        }
    }

    // -- Observe ViewModel results ---------------------------------------------
    private fun observeResults() {
        viewModel.saveResult.observe(this) { result ->
            when (result) {
                is BankAccountViewModel.SaveResult.Success -> {
                    Toast.makeText(this, "Account added successfully!", Toast.LENGTH_SHORT).show()
                    finish()   // go back to AccountsActivity
                }
                is BankAccountViewModel.SaveResult.Error -> {
                    // Show inline error on the relevant field if possible
                    when {
                        result.message.contains("nickname", ignoreCase = true) ->
                            nicknameLayout.error = result.message
                        result.message.contains("account number", ignoreCase = true) ->
                            accountNumberLayout.error = result.message
                        else ->
                            Snackbar.make(
                                findViewById(R.id.main),
                                result.message,
                                Snackbar.LENGTH_LONG
                            ).show()
                    }
                }
            }
        }
    }
}