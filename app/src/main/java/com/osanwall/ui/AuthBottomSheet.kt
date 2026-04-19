package com.osanwall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.osanwall.data.repository.AuthRepository
import com.osanwall.ui.components.GradientButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthBottomSheet(
    onDismiss: () -> Unit,
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            viewModel.clearError()
            onAuthenticated()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "OsanWall",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Sign in or create an account to sync your world.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0; viewModel.clearError() },
                    text = { Text("Sign In") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1; viewModel.clearError() },
                    text = { Text("Sign Up") }
                )
            }

            Spacer(Modifier.height(16.dp))

            if (tab == 1) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.lowercase().replace(" ", "") },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    prefix = { Text("@") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = if (tab == 0) ImeAction.Done else ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (tab == 1 && password.isNotEmpty() && password.length < 6) {
                        Text("Min 6 characters")
                    }
                },
                colors = fieldColors()
            )

            uiState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = {
                    email = AuthRepository.DEMO_EMAIL
                    password = AuthRepository.DEMO_PASSWORD
                    if (tab == 1 && username.isBlank()) username = "demo_explorer"
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Fill demo credentials", fontWeight = FontWeight.Bold)
            }

            if (tab == 0) {
                GradientButton(
                    text = "Sign In",
                    onClick = { viewModel.signIn(email, password) },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.isLoading,
                    enabled = email.isNotBlank() && password.isNotBlank()
                )
            } else {
                GradientButton(
                    text = "Create Account",
                    onClick = { viewModel.signUp(email, password, username) },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.isLoading,
                    enabled = email.isNotBlank() && password.length >= 6 && username.length >= 3
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)
