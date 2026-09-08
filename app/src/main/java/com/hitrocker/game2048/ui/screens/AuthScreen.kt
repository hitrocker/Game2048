package com.hitrocker.game2048.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitrocker.game2048.ui.theme.BoardBackground
import com.hitrocker.game2048.ui.theme.PlayAccent
import com.hitrocker.game2048.ui.theme.TileTextDark
import com.hitrocker.game2048.viewmodel.AuthViewModel

/**
 * Custom, in-app sign-in screen styled to match the game (cream/gold). Supports email/password
 * sign-in and account creation, a "Continue with Google" button (Credential Manager), and password
 * reset. Calls [onAuthComplete] once a real sign-in succeeds.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onAuthComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var createMode by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.signedIn.collect { onAuthComplete() }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PlayAccent,
        focusedLabelColor = PlayAccent,
        cursorColor = PlayAccent,
        focusedTextColor = TileTextDark,
        unfocusedTextColor = TileTextDark
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = if (createMode) "Create account" else "Sign in",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlayAccent)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Text("2048", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }

            Text(
                text = "Sign in to save your scores to the global leaderboard.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearMessages() },
                label = { Text("Email") },
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; viewModel.clearMessages() },
                label = { Text("Password") },
                singleLine = true,
                colors = fieldColors,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = TileTextDark
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (createMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) { name = it; viewModel.clearMessages() } },
                    label = { Text("Display name") },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFD32F2F), fontSize = 14.sp)
            }
            state.info?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFF2E7D32), fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))

            // Primary action button.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (state.loading) PlayAccent.copy(alpha = 0.6f) else PlayAccent)
                    .clickable(enabled = !state.loading) {
                        if (createMode) viewModel.createAccount(email, password, name)
                        else viewModel.signIn(email, password)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = if (createMode) "Create account" else "Sign in",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            if (!createMode) {
                TextButton(
                    onClick = { viewModel.resetPassword(email) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Forgot password?", color = TileTextDark)
                }
            }

            // Divider with "or".
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f), color = BoardBackground.copy(alpha = 0.5f))
                Text(
                    "  or  ",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
                Divider(modifier = Modifier.weight(1f), color = BoardBackground.copy(alpha = 0.5f))
            }

            OutlinedButton(
                onClick = { viewModel.signInWithGoogle(context) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Continue with Google",
                    color = TileTextDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (createMode) "Already have an account?" else "New here?",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                TextButton(onClick = { createMode = !createMode; viewModel.clearMessages() }) {
                    Text(
                        text = if (createMode) "Sign in" else "Create account",
                        color = PlayAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
