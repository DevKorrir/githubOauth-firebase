package com.example.githublogin

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ==================================================
// AuthState.kt - UI State representation
// ==================================================
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

// ==================================================
// AuthRepository.kt - Data layer following KISS principle
// ==================================================


// AuthRepository.kt

class AuthRepository {
    private val firebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "GitHubAuth"
    }

    // Simple method to get current user
    fun getCurrentUser() = firebaseAuth.currentUser

    // GitHub OAuth sign-in with proper Activity context - fixes the redirect issue
    suspend fun signInWithGitHub(activity: Activity): Result<String> {

        Log.d(TAG, "=== Starting GitHub OAuth Sign-In ===")
        Log.d(TAG, "Firebase Auth instance: ${firebaseAuth.app.name}")
        Log.d(TAG, "Activity: ${activity::class.java.simpleName}")

        return try {
            // Configure GitHub OAuth provider with proper scopes
            val provider = OAuthProvider.newBuilder("github.com").apply {
                Log.d(TAG, "Setting GitHub OAuth scopes...")
                // Add required GitHub scopes for basic user info
                setScopes(listOf("user:email", "read:user"))

                Log.d(TAG, "Adding custom OAuth parameters...")

                // Add custom parameters if needed
                addCustomParameters(mapOf(
                    "allow_signup" to "true", // Allow new account creation
                    //"login" to "", // Optional: pre-fill username
                ))
            }.build()

            Log.d(TAG, "OAuth provider configured successfully")
            Log.d(TAG, "Attempting Firebase OAuth sign-in...")

            // Alternative approach: Check if pending auth result exists first
            val pendingResult = firebaseAuth.pendingAuthResult
            val authResult = if (pendingResult != null) {
                Log.d(TAG, "Found pending auth result, completing...")
                pendingResult.await()
            } else {
                Log.d(TAG, "No pending result, starting new OAuth flow...")
                firebaseAuth.startActivityForSignInWithProvider(activity, provider).await()
            }

            Log.d(TAG, "OAuth flow completed, processing result...")

            val user = authResult.user
            if (user != null) {
                Log.d(TAG, "User authenticated successfully:")
                Log.d(TAG, "- UID: ${user.uid}")
                Log.d(TAG, "- Email: ${user.email}")
                Log.d(TAG, "- Display Name: ${user.displayName}")
                Log.d(TAG, "- Provider: ${user.providerData.joinToString { it.providerId }}")

                // Get additional user info from OAuth result
                val additionalInfo = authResult.additionalUserInfo
                Log.d(TAG, "Additional user info:")
                Log.d(TAG, "- Username: ${additionalInfo?.username}")
                Log.d(TAG, "- Is new user: ${additionalInfo?.isNewUser}")
                Log.d(TAG, "- Provider ID: ${additionalInfo?.providerId}")

                // Get user display name with multiple fallbacks
                val userName = user.displayName
                    ?: user.email
                    ?: additionalInfo?.username
                    ?: "GitHub User"

                Log.d(TAG, "Final username: $userName")
                Log.d(TAG, "=== GitHub OAuth Sign-In SUCCESS ===")

                Result.success(userName)
            } else {
                Log.e(TAG, "Authentication completed but user is null")
                Log.e(TAG, "=== GitHub OAuth Sign-In FAILED ===")
                Result.failure(Exception("Authentication failed - no user data received"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "=== GitHub OAuth Sign-In ERROR ===")
            Log.e(TAG, "Exception type: ${e::class.java.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)

            // Enhanced error handling with more specific cases
            val errorMessage = when (e) {
                is FirebaseAuthException -> {
                    Log.e(TAG, "Firebase Auth Error Code: ${e.errorCode}")
                    when (e.errorCode) {
                        "ERROR_WEB_CONTEXT_CANCELED" -> {
                            "The sign-in window was closed. This might happen due to:\n" +
                                    "• Web view loading issues\n" +
                                    "• Browser compatibility problems\n" +
                                    "• Network connectivity issues\n" +
                                    "Please try again or restart the app."
                        }
                        "ERROR_WEB_CONTEXT_REQUEST_FAILED" -> {
                            "OAuth request failed. Please check your internet connection."
                        }
                        "ERROR_WEB_INTERNAL_ERROR" -> {
                            "Internal OAuth error. This might be a configuration issue."
                        }
                        "ERROR_INVALID_CREDENTIAL" -> {
                            "Invalid credentials. Please check GitHub OAuth app configuration."
                        }
                        else -> {
                            "Firebase Auth Error (${e.errorCode}): ${e.message}"
                        }
                    }
                }
                else -> {
                    when {
                        e.message?.contains("DEVELOPER_ERROR") == true -> {
                            "Developer configuration error. Check Firebase console and GitHub OAuth app setup."
                        }
                        e.message?.contains("NETWORK_ERROR") == true -> {
                            "Network error. Please check your internet connection and try again."
                        }
                        e.message?.contains("USER_CANCELLED") == true -> {
                            "Sign in was cancelled by user."
                        }
                        e.message?.contains("browser sessionStorage") == true -> {
                            "Browser storage issue. Try clearing app data or restarting the app."
                        }
                        e.message?.contains("storage-partitioned") == true -> {
                            "Storage access issue. This might be a device/browser compatibility problem."
                        }
                        else -> {
                            e.message ?: "Unknown authentication error occurred"
                        }
                    }
                }
            }

            Log.e(TAG, "Final error message: $errorMessage")
            Result.failure(Exception(errorMessage))
        }
    }

    // Sign out with logging
    fun signOut() {
        Log.d(TAG, "=== Starting Sign Out ===")
        val currentUser = firebaseAuth.currentUser
        Log.d(TAG, "Current user before sign out: ${currentUser?.email}")

        firebaseAuth.signOut()

        Log.d(TAG, "Sign out completed")
        Log.d(TAG, "Current user after sign out: ${firebaseAuth.currentUser}")
        Log.d(TAG, "=== Sign Out Complete ===")
    }

    // Additional helper method to check Firebase Auth state
    fun logAuthState() {
        Log.d(TAG, "=== Current Auth State ===")
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User is signed in:")
            Log.d(TAG, "- UID: ${currentUser.uid}")
            Log.d(TAG, "- Email: ${currentUser.email}")
            Log.d(TAG, "- Display Name: ${currentUser.displayName}")
            Log.d(TAG, "- Email Verified: ${currentUser.isEmailVerified}")
            Log.d(TAG, "- Providers: ${currentUser.providerData.joinToString { it.providerId }}")
        } else {
            Log.d(TAG, "No user currently signed in")
        }
        Log.d(TAG, "Firebase App: ${firebaseAuth.app.name}")
        Log.d(TAG, "=== End Auth State ===")
    }
}

// ==================================================
// AuthViewModel.kt - MVVM ViewModel with minimal complexity
// ==================================================


class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    // Single source of truth for UI state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        Log.d(TAG, "AuthViewModel initialized")
        // Check if user is already signed in - HITL principle
        checkCurrentUser()
        // Log current auth state for debugging
        repository.logAuthState()
    }

    // New function to explicitly set an error state from outside
    fun setActivityError(errorMessage: String) {
        Log.e(TAG, "Setting activity error: $errorMessage")
        _authState.value = AuthState.Error(errorMessage)
    }

    // Human in the loop - check existing session with logging
    private fun checkCurrentUser() {
        Log.d(TAG, "Checking for existing user session...")
        val currentUser = repository.getCurrentUser()
        if (currentUser != null) {
            Log.d(TAG, "Found existing user session: ${currentUser.email}")
            _authState.value = AuthState.Success(
                currentUser.displayName ?: currentUser.email ?: "GitHub User"
            )
        } else {
            Log.d(TAG, "No existing user session found")
            _authState.value = AuthState.Idle
        }
    }

    // Updated to require Activity context with comprehensive logging
    fun signInWithGitHub(activity: Activity) {
        Log.d(TAG, "=== Starting GitHub Sign-In Process ===")
        Log.d(TAG, "Current state: ${_authState.value}")

        viewModelScope.launch {
            try {
                Log.d(TAG, "Setting state to Loading...")
                _authState.value = AuthState.Loading

                Log.d(TAG, "Calling repository signInWithGitHub...")
                val result = repository.signInWithGitHub(activity)

                result.onSuccess { userName ->
                    Log.d(TAG, "Sign-in successful for user: $userName")
                    _authState.value = AuthState.Success(userName)
                    Log.d(TAG, "State updated to Success")
                }.onFailure { error ->
                    Log.e(TAG, "Sign-in failed with error: ${error.message}")
                    _authState.value = AuthState.Error(
                        error.message ?: "Unknown error occurred"
                    )
                    Log.d(TAG, "State updated to Error")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in signInWithGitHub: ${e.message}", e)
                _authState.value = AuthState.Error(
                    "Unexpected error: ${e.message ?: "Unknown error"}"
                )
            }
        }

        Log.d(TAG, "=== GitHub Sign-In Process Initiated ===")
    }

    // Simple sign out with logging
    fun signOut() {
        Log.d(TAG, "=== Starting Sign Out Process ===")
        Log.d(TAG, "Current state: ${_authState.value}")

        repository.signOut()
        _authState.value = AuthState.Idle

        Log.d(TAG, "Sign out completed, state reset to Idle")
        Log.d(TAG, "=== Sign Out Process Complete ===")
    }

    // Reset error state with logging
    fun clearError() {
        Log.d(TAG, "Clearing error state")
        Log.d(TAG, "Previous state: ${_authState.value}")
        _authState.value = AuthState.Idle
        Log.d(TAG, "State reset to Idle")
    }

    // Add method to manually refresh auth state (useful for debugging)
    fun refreshAuthState() {
        Log.d(TAG, "Manually refreshing auth state...")
        repository.logAuthState()
        checkCurrentUser()
    }

    override fun onCleared() {
        Log.d(TAG, "AuthViewModel cleared")
        super.onCleared()
    }
}


@Composable
fun LoginScreen(
    viewModel: AuthViewModel
) {

    val context = LocalContext.current

    // Observe single state - MVVM pattern
    val authStateValue by viewModel.authState.collectAsState()

    // Get Activity context for OAuth flow
    val activity = context as? Activity
    if (activity != null) {
        // show small error UI or return early
        LaunchedEffect(Unit) {
            Log.e("LoginScreen", "Activity context is null — cannot start OAuth")
            // Call the new public method on the ViewModel
            viewModel.setActivityError("App cannot start OAuth: activity unavailable")
        }


        // Single column layout - KISS principle
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App branding section
            AppHeader()

            Spacer(modifier = Modifier.height(48.dp))

            // State-based UI rendering - clean and simple
            when (val currentAuthState = authStateValue) { // Capture the value here
                is AuthState.Idle -> {
                    LoginContent(onSignIn = { viewModel.signInWithGitHub(activity) })
                }

                is AuthState.Loading -> {
                    LoadingContent()
                }

                is AuthState.Success -> {
                    SuccessContent(
                        userName = currentAuthState.user,
                        onSignOut = viewModel::signOut
                    )
                }

                is AuthState.Error -> {
                    ErrorContent(
                        errorMessage = currentAuthState.message,
                        onRetry = { viewModel.signInWithGitHub(activity) },
                        onDismiss = viewModel::clearError
                    )
                }
            }
        }
        return
    }
}

// Clean component separation - Single Responsibility Principle
@Composable
private fun AppHeader() {
    Text(
        text = "GitHubLogin",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Text(
        text = "Sign in with your GitHub account",
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun LoginContent(onSignIn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // GitHub icon placeholder - in real app, use actual GitHub icon
            Icon(
                imageVector = Icons.Default.Login,
                contentDescription = "GitHub Login",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Single action button - KISS principle
            Button(
                onClick = {
                    Log.d("LoginContent", "GitHub login button clicked")
                    onSignIn()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF24292e) // GitHub dark color
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continue with GitHub",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Debug info card (can be removed in production)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Debug Info:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Check Logcat for detailed OAuth logs\n" +
                                "• Look for 'GitHubAuth' and 'AuthViewModel' tags\n" +
                                "• Ensure Firebase project ID is correct\n" +
                                "• Verify GitHub OAuth callback URL",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Signing you in...",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SuccessContent(
    userName: String,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = userName,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sign In Failed",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Show detailed error message and specific guidance for web context cancellation
            Text(
                text = errorMessage,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Show specific troubleshooting for cancellation error
            if (errorMessage.contains("sign-in window was closed") || errorMessage.contains("cancelled")) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Quick Fixes:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Wait for the GitHub page to fully load\n" +
                                    "2. Don't close the sign-in window manually\n" +
                                    "3. Check your internet connection\n" +
                                    "4. Try restarting the app if issue persists\n" +
                                    "5. Ensure you have a GitHub account",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Show general troubleshooting steps
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "General Troubleshooting:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Check your internet connection\n" +
                                "• Verify Firebase project configuration\n" +
                                "• Check GitHub OAuth app settings\n" +
                                "• Try clearing app data if issue persists",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Dismiss")
                }

                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
