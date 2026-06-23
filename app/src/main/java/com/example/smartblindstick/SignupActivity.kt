package com.example.smartblindstick

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        val emailEt = findViewById<TextInputEditText>(R.id.emailEditText)
        val passwordEt = findViewById<TextInputEditText>(R.id.passwordEditText)
        val signupBtn = findViewById<MaterialButton>(R.id.signupButton)
        val backBtn = findViewById<MaterialButton>(R.id.backButton)
        val loginText = findViewById<TextView>(R.id.loginText)

        signupBtn.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            signupBtn.isEnabled = false
            signupBtn.text = "Creating..."

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    val uid = user?.uid
                    val userMap = mapOf("email" to email)

                    if (uid != null) {
                        FirebaseDatabase.getInstance().getReference("users").child(uid).setValue(userMap)
                    }

                    user?.sendEmailVerification()?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Verify your email!", Toast.LENGTH_LONG).show()
                            auth.signOut()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    signupBtn.isEnabled = true
                }
        }

        // FIXED: Redirection to Login
        loginText.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        backBtn.setOnClickListener { finish() }
    }
}