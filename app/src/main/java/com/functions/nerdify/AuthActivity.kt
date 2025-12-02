package com.functions.nerdify

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class AuthActivity : AppCompatActivity() {

    // --- Constants ---
    companion object {
        private const val RC_SIGN_IN = 9001 // Request code for Google Sign In intent
        private const val TAG = "NerdifyAuth"
    }

    // --- UI Elements ---
    private lateinit var authFormTitle: TextView
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var authPrimaryButton: Button
    private lateinit var googleSignInButton: Button
    private lateinit var toggleAuthModeText: TextView

    // --- Auth Properties ---
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient // Google Sign-In client
    private var isSignInMode: Boolean = true // True = Sign In, False = Sign Up

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        
        firebaseAuth = FirebaseAuth.getInstance()
        initializeViews()
        configureGoogleSignIn() // Configure the Google Sign-In options

        if (firebaseAuth.currentUser != null) {
            goToMainActivity()
            return
        }

        // --- Setup Listeners ---
        authPrimaryButton.setOnClickListener { handlePrimaryAuthAction() }
        toggleAuthModeText.setOnClickListener { toggleAuthMode() }
        
        // **NEW: Google Sign In Listener**
        googleSignInButton.setOnClickListener { 
            signInWithGoogleIntent() 
        }

        updateUiForMode()
    }

    // --- Google Sign-In Setup ---

    private fun configureGoogleSignIn() {
        // IMPORTANT: You must replace R.string.default_web_client_id with your actual Web Client ID
        // obtained from the Firebase Console (Settings -> General -> Web API Key).
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) 
            .requestEmail()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogleIntent() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    // --- Handle Intent Result ---

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Google Sign In Failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Google auth successful")
                    Toast.makeText(this, "Signed in with Google!", Toast.LENGTH_SHORT).show()
                    goToMainActivity()
                } else {
                    Log.w(TAG, "Google auth failed", task.exception)
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // --- Other Core Functions (Unchanged) ---
    private fun initializeViews() {
        authFormTitle = findViewById(R.id.auth_form_title)
        emailEditText = findViewById(R.id.email_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        authPrimaryButton = findViewById(R.id.auth_primary_button)
        googleSignInButton = findViewById(R.id.google_sign_in_button)
        toggleAuthModeText = findViewById(R.id.toggle_auth_mode_text)
    }

    private fun handlePrimaryAuthAction() {
        // ... (Sign In / Sign Up logic)
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isSignInMode) {
            signInWithEmail(email, password)
        } else {
            signUpWithEmail(email, password)
        }
    }
    
    // ... (signInWithEmail, signUpWithEmail, toggleAuthMode, updateUiForMode, goToMainActivity functions) ...

    /**
     * Navigates the user to the main application screen.
     */
    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}