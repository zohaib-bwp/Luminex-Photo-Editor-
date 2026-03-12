package com.luminex.photoenhancer.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.luminex.photoenhancer.R
import com.luminex.photoenhancer.database.AppDatabase
import com.luminex.photoenhancer.databinding.ActivityAuthBinding
import com.luminex.photoenhancer.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var database: AppDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var isLoginMode = true

    companion object {
        private const val TAG = "AuthActivity"
    }

    // Activity Result Launcher for Google Sign-In
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null && account.idToken != null) {
                    firebaseAuthWithGoogle(account.idToken!!)
                } else {
                    showError("Google sign-in failed: Invalid account")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign in failed", e)
                showError("Google sign-in failed: ${e.localizedMessage}")
            }
        } else {
            showError("Google sign-in cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase and Database
        auth = Firebase.auth
        database = AppDatabase.getInstance(this)

        setupGoogleSignIn()
        setupUI()
        setupClickListeners()
    }

    private fun setupGoogleSignIn() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup Google Sign-In", e)
            // Disable Google Sign-In button if setup fails
            binding.btnGoogleAuth.isEnabled = false
            binding.btnGoogleAuth.text = "Google Sign-In unavailable"
        }
    }

    private fun setupUI() {
        updateUIMode()
    }

    private fun setupClickListeners() {
        binding.btnAuth.setOnClickListener {
            if (isLoginMode) {
                performLogin()
            } else {
                performRegistration()
            }
        }

        binding.btnToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUIMode()
        }

        binding.btnSkipAuth.setOnClickListener {
            createOfflineUser()
        }

        binding.btnGoogleAuth.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        try {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Google Sign-In", e)
            showError("Failed to start Google Sign-In")
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = withContext(Dispatchers.IO) {
                    auth.signInWithCredential(credential).await()
                }
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    // Sync Firebase user with local Room database
                    val newUser = User(
                        userId = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        username = firebaseUser.displayName ?: "User",
                        fullName = firebaseUser.displayName ?: "Google User",
                        isOnlineUser = true
                    )

                    withContext(Dispatchers.IO) {
                        database.userDao().insertUser(newUser)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AuthActivity,
                            "Welcome ${firebaseUser.displayName}!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToMain()
                    }
                } else {
                    showError("Authentication failed: No user data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase auth failed", e)
                showError("Authentication failed: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateUIMode() {
        if (isLoginMode) {
            binding.titleAuth.text = "Welcome Back!"
            binding.subtitleAuth.text = "Sign in to continue enhancing"
            binding.btnAuth.text = "Sign In"
            binding.btnToggleMode.text = "Don't have an account? Sign Up"
            binding.fullNameLayout.visibility = android.view.View.GONE
        } else {
            binding.titleAuth.text = "Create Account"
            binding.subtitleAuth.text = "Join Luminex community"
            binding.btnAuth.text = "Sign Up"
            binding.btnToggleMode.text = "Already have an account? Sign In"
            binding.fullNameLayout.visibility = android.view.View.VISIBLE
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Validation
        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Invalid email format"
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserByEmail(email)
                }

                if (user != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AuthActivity,
                            "Welcome back, ${user.username}!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToMain()
                    }
                } else {
                    // Auto-create user for demo purposes
                    val newUser = User(
                        userId = UUID.randomUUID().toString(),
                        email = email,
                        username = email.substringBefore("@"),
                        fullName = email.substringBefore("@"),
                        isOnlineUser = false
                    )

                    withContext(Dispatchers.IO) {
                        database.userDao().insertUser(newUser)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AuthActivity,
                            "Welcome, ${newUser.username}!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToMain()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                showError("Login failed: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun performRegistration() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val fullName = binding.etFullName.text.toString().trim()

        // Validation
        if (fullName.isEmpty()) {
            binding.etFullName.error = "Full name is required"
            return
        }
        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Invalid email format"
            return
        }
        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            try {
                val existingUser = withContext(Dispatchers.IO) {
                    database.userDao().getUserByEmail(email)
                }

                if (existingUser != null) {
                    showError("Account already exists. Please sign in.")
                } else {
                    val newUser = User(
                        userId = UUID.randomUUID().toString(),
                        email = email,
                        username = email.substringBefore("@"),
                        fullName = fullName,
                        isOnlineUser = false
                    )

                    withContext(Dispatchers.IO) {
                        database.userDao().insertUser(newUser)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AuthActivity,
                            "Account created successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToMain()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed", e)
                showError("Registration failed: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun createOfflineUser() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val offlineUser = User(
                    userId = "offline_" + System.currentTimeMillis(),
                    email = "offline@luminex.local",
                    username = "Guest",
                    fullName = "Guest User",
                    isOnlineUser = false
                )

                withContext(Dispatchers.IO) {
                    database.userDao().insertUser(offlineUser)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AuthActivity,
                        "Continuing as Guest",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create offline user", e)
                showError("Failed to skip login: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun showLoading(show: Boolean) {
        binding.btnAuth.isEnabled = !show
        binding.btnGoogleAuth.isEnabled = !show
        binding.btnSkipAuth.isEnabled = !show
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}