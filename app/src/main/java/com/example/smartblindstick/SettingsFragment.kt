package com.example.smartblindstick

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SettingsFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        auth = FirebaseAuth.getInstance()
        sharedPreferences = requireActivity().getSharedPreferences("NazarSettings", Context.MODE_PRIVATE)

        // UI Bindings
        val switchLidar = view.findViewById<MaterialSwitch>(R.id.switchLidar)
        val switchIr = view.findViewById<MaterialSwitch>(R.id.switchIr)
        val switchWater = view.findViewById<MaterialSwitch>(R.id.switchWater)
        val btnTheme = view.findViewById<LinearLayout>(R.id.btnTheme)
        val tvCurrentTheme = view.findViewById<TextView>(R.id.tvCurrentTheme)
        val btnLanguage = view.findViewById<LinearLayout>(R.id.btnLanguage)
        val tvCurrentLanguage = view.findViewById<TextView>(R.id.tvCurrentLanguage)
        val btnPrivacyPolicy = view.findViewById<MaterialCardView>(R.id.btnPrivacyPolicy)
        val btnDeleteAccount = view.findViewById<MaterialButton>(R.id.btnDeleteAccount)

        // 1. Notifications Logic
        switchLidar.isChecked = sharedPreferences.getBoolean("notify_lidar", true)
        switchIr.isChecked = sharedPreferences.getBoolean("notify_ir", true)
        switchWater.isChecked = sharedPreferences.getBoolean("notify_water", true)

        switchLidar.setOnCheckedChangeListener { _, isChecked -> sharedPreferences.edit().putBoolean("notify_lidar", isChecked).apply() }
        switchIr.setOnCheckedChangeListener { _, isChecked -> sharedPreferences.edit().putBoolean("notify_ir", isChecked).apply() }
        switchWater.setOnCheckedChangeListener { _, isChecked -> sharedPreferences.edit().putBoolean("notify_water", isChecked).apply() }

        // 2. Theme Logic
        tvCurrentTheme.text = sharedPreferences.getString("app_theme", "System Default")
        btnTheme.setOnClickListener {
            val themes = arrayOf("Light", "Dark", "System Default")
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.setting_theme))
                .setItems(themes) { _, which ->
                    val selectedTheme = themes[which]
                    sharedPreferences.edit().putString("app_theme", selectedTheme).apply()
                    tvCurrentTheme.text = selectedTheme
                    applyTheme(selectedTheme)
                }
                .show()
        }

        // 3. Language Logic
        tvCurrentLanguage.text = sharedPreferences.getString("app_language", "English")
        btnLanguage.setOnClickListener {
            val languages = arrayOf("English", "Hindi", "Marathi", "Urdu")
            val codes = arrayOf("en", "hi", "mr", "ur")

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.setting_lang))
                .setItems(languages) { _, which ->
                    val selectedLang = languages[which]
                    val selectedCode = codes[which]

                    sharedPreferences.edit()
                        .putString("app_language", selectedLang)
                        .putString("app_lang_code", selectedCode)
                        .apply()

                    Toast.makeText(requireContext(), "Updating Language: $selectedLang...", Toast.LENGTH_SHORT).show()

                    val appLocale = LocaleListCompat.forLanguageTags(selectedCode)
                    AppCompatDelegate.setApplicationLocales(appLocale)
                }
                .show()
        }

        btnPrivacyPolicy.setOnClickListener { showPrivacyPolicy() }
        btnDeleteAccount.setOnClickListener { showDeleteAccountStep1() }

        return view
    }

    private fun applyTheme(themeName: String) {
        when (themeName) {
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun showPrivacyPolicy() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val padding = 64

        val textView = TextView(requireContext()).apply {
            setPadding(padding, padding, padding, padding)
            textSize = 14f

            // Improves readability of point-wise text
            setLineSpacing(0f, 1.2f)

            // Retrieves the CDATA string from strings.xml and parses HTML tags (bold, br)
            val policyText = getString(R.string.privacy_policy_text)
            text = Html.fromHtml(policyText, Html.FROM_HTML_MODE_COMPACT)

            setTextColor(resources.getColor(R.color.textPrimary, null))
        }

        val scrollView = ScrollView(requireContext())
        scrollView.addView(textView)
        bottomSheet.setContentView(scrollView)
        bottomSheet.show()
    }

    private fun showDeleteAccountStep1() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.btn_delete_acc))
            .setMessage(getString(R.string.delete_account_msg))
            .setPositiveButton("Yes") { _, _ -> showDeleteAccountStep2() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showDeleteAccountStep2() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(60, 20, 60, 0)
            layoutParams = lp
        }

        val input = EditText(requireContext()).apply {
            hint = "type 'delete'"
            gravity = Gravity.CENTER
        }
        container.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Deletion")
            .setMessage("Type 'delete' to permanently remove your account:")
            .setView(container)
            .setPositiveButton("Confirm") { _, _ ->
                if (input.text.toString().trim().lowercase() == "delete") executePermanentDeletion()
                else Toast.makeText(requireContext(), "Incorrect text.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executePermanentDeletion() {
        val user = auth.currentUser
        val uid = user?.uid
        if (user != null && uid != null) {
            FirebaseDatabase.getInstance().getReference("users").child(uid).removeValue().addOnSuccessListener {
                user.delete().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        startActivity(Intent(requireContext(), LoginActivity::class.java))
                        requireActivity().finish()
                    }
                }
            }
        }
    }
}