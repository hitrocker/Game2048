package com.hitrocker.game2048.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Fetches a Google ID token via Credential Manager.
 *
 * Kept free of any app/game dependencies so it can be reused as-is: construct it with your Firebase
 * web client id (`R.string.default_web_client_id`) and call [fetch] from a coroutine. The returned
 * token is exchanged for a Firebase credential by [AuthManager.signInWithGoogleIdToken] /
 * [AuthManager.reauthenticateWithGoogle].
 *
 * [fetch] throws the standard `androidx.credentials` exceptions, which the caller is expected to
 * handle (notably `GetCredentialCancellationException` when the user dismisses the account picker).
 */
class GoogleIdTokenProvider(private val webClientId: String) {

    suspend fun fetch(context: Context): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("Unexpected credential type")
    }
}
