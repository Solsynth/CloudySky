package dev.solsynth.cloudysky

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.solsynth.cloudysky.auth.AuthRepository
import dev.solsynth.cloudysky.auth.CurrentAccount
import dev.solsynth.cloudysky.ui.theme.CloudySkyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CloudySkyTheme {
                val context = LocalContext.current
                val authRepository = remember { AuthRepository(context) }
                val authState by authRepository.authState.collectAsState()
                val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
                    Log.d(TAG, "auth launcher returned: hasData=${result.data != null}")
                    authRepository.handleAuthorizationResult(result.data)
                }
                var currentAccount by remember { mutableStateOf<CurrentAccount?>(null) }
                var loadingAccount by remember { mutableStateOf(false) }

                LaunchedEffect(authState.isAuthorized) {
                    Log.d(TAG, "auth state changed: authorized=${authState.isAuthorized}")
                    if (!authState.isAuthorized) {
                        currentAccount = null
                        return@LaunchedEffect
                    }

                    loadingAccount = true
                    currentAccount = authRepository.fetchCurrentAccount()
                    loadingAccount = false
                    Log.d(TAG, "account loaded: present=${currentAccount != null}")
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AuthScreen(
                        isSignedIn = authState.isAuthorized,
                        currentAccount = currentAccount,
                        loadingAccount = loadingAccount,
                        onSignIn = {
                            Log.d(TAG, "sign in clicked")
                            launcher.launch(authRepository.createAuthorizationIntent())
                        },
                        onSignOut = authRepository::signOut,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "CloudySkyMain"
    }
}

@Composable
fun AuthScreen(
    isSignedIn: Boolean,
    currentAccount: CurrentAccount?,
    loadingAccount: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = if (isSignedIn) "Signed in" else "Signed out")
                Text(text = "OIDC via AppAuth is wired up.")
                if (loadingAccount) {
                    Text(text = "Loading account...")
                } else if (currentAccount != null) {
                    Text(text = currentAccount.displayName)
                    Text(text = currentAccount.bio.ifBlank { currentAccount.language })
                }
                Button(onClick = if (isSignedIn) onSignOut else onSignIn) {
                    Text(text = if (isSignedIn) "Sign out" else "Sign in")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    CloudySkyTheme {
        AuthScreen(isSignedIn = false, currentAccount = null, loadingAccount = false, onSignIn = {}, onSignOut = {})
    }
}
