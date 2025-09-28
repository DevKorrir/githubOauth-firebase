package com.example.githublogin


// ==================================================
// LoginScreen.kt - Single Page UI with clean composition
// ==================================================
import android.annotation.SuppressLint
import android.app.Activity
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

    // Simple method to get current user
    fun getCurrentUser() = firebaseAuth.currentUser

    // GitHub OAuth sign-in with proper Activity context - fixes the redirect issue
    suspend fun signInWithGitHub(activity: Activity): Result<String> {
        return try {
            // Configure GitHub OAuth provider with proper scopes
            val provider = OAuthProvider.newBuilder("github.com").apply {
                // Add required GitHub scopes for basic user info
                setScopes(listOf("user:email", "read:user"))

                // Add custom parameters if needed
                addCustomParameters(mapOf(
                    "allow_signup" to "false" // Optional: prevent new account creation
                ))
            }.build()

            // Use the Activity context for proper redirect handling
            val result = firebaseAuth.startActivityForSignInWithProvider(activity, provider).await()

            val user = result.user
            if (user != null) {
                // Get user display name, fallback to email, then to GitHub username
                val userName = user.displayName
                    ?: user.email
                    ?: result.additionalUserInfo?.username
                    ?: "GitHub User"

                Result.success(userName)
            } else {
                Result.failure(Exception("Authentication failed - no user data received"))
            }
        } catch (e: Exception) {
            // Better error handling for common OAuth issues
            val errorMessage = when {
                e.message?.contains("DEVELOPER_ERROR") == true ->
                    "Developer configuration error. Check Firebase console setup."
                e.message?.contains("NETWORK_ERROR") == true ->
                    "Network error. Please check your internet connection."
                e.message?.contains("USER_CANCELLED") == true ->
                    "Sign in was cancelled."
                else -> e.message ?: "Unknown authentication error"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    // Sign out - simple and clean
    fun signOut() {
        firebaseAuth.signOut()
    }
}

// ==================================================
// AuthViewModel.kt - MVVM ViewModel with minimal complexity
// ==================================================


class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    // Single source of truth for UI state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Check if user is already signed in - HITL principle
        checkCurrentUser()
    }

    // Human in the loop - check existing session
    private fun checkCurrentUser() {
        val currentUser = repository.getCurrentUser()
        if (currentUser != null) {
            _authState.value = AuthState.Success(
                currentUser.displayName ?: currentUser.email ?: "GitHub User"
            )
        }
    }

    // Single action method - KISS principle
    fun signInWithGitHub(activity: Activity) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            repository.signInWithGitHub(activity)
                .onSuccess { userName ->
                    _authState.value = AuthState.Success(userName)
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(
                        error.message ?: "Unknown error occurred"
                    )
                }
        }
    }

    // Simple sign out
    fun signOut() {
        repository.signOut()
        _authState.value = AuthState.Idle
    }

    // Reset error state for better UX
    fun clearError() {
        _authState.value = AuthState.Idle
    }
}

@SuppressLint("ContextCastToActivity")
@Composable
fun LoginScreen(
    viewModel: AuthViewModel
) {

    // Get Activity context for OAuth flow
    val context = LocalContext.current as Activity

    // Observe single state - MVVM pattern
    val authStateValue by viewModel.authState.collectAsState()

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
                LoginContent(onSignIn = { viewModel.signInWithGitHub(context) })
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
                    onRetry = { viewModel.signInWithGitHub(context) },
                    onDismiss = viewModel::clearError
                )
            }
        }
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
                onClick = onSignIn,
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

            Text(
                text = errorMessage,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

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
