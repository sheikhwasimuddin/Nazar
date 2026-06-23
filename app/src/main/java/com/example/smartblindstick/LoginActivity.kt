package com.example.smartblindstick

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // 🔥 NEW: Google Auth variables
    private lateinit var googleSignInClient: GoogleSignInClient
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.e("GOOGLE_AUTH", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // ✅ BUG FIX & Gatekeeper: Auto-login check.
        // Skips to Dashboard ONLY if logged in AND email is verified.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (currentUser.isEmailVerified) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            } else {
                // Kick out unverified users trying to auto-login
                auth.signOut()
            }
        }

        setContentView(R.layout.activity_login)

        // 🔥 NEW: Setup Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val emailEt = findViewById<TextInputEditText>(R.id.emailEt)
        val passwordEt = findViewById<TextInputEditText>(R.id.passwordEt)
        val loginBtn = findViewById<MaterialButton>(R.id.loginBtn)
        val googleBtn = findViewById<MaterialButton>(R.id.googleBtn) // 🔥 NEW: Google Button reference

        val signupText = findViewById<android.widget.TextView>(R.id.signupText)

        // 🔥 LOGIN BUTTON (Untouched logic, just added verification check)
        loginBtn.setOnClickListener {

            val email = emailEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginBtn.isEnabled = false
            loginBtn.text = "Logging in..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {

                    // ✅ NEW: Verification Gatekeeper
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        // User tried to log in without verifying
                        Toast.makeText(this, "Please verify your email first!", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        loginBtn.isEnabled = true
                        loginBtn.text = "Login"
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login Failed: ${it.message}", Toast.LENGTH_LONG).show()
                    loginBtn.isEnabled = true
                    loginBtn.text = "Login"
                }
        }

        // 🔥 NEW: GOOGLE BUTTON CLICK
        googleBtn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // 🔥 GO TO SIGNUP (Untouched)
        signupText.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    // 🔥 NEW: Connect Google Account to Firebase
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Google Login Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Firebase Auth Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}