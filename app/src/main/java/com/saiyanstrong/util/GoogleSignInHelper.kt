package com.saiyanstrong.util

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.saiyanstrong.BuildConfig
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

data class GoogleSignInTokens(val idToken: String, val rawNonce: String)

class GoogleSignInHelper @Inject constructor() {

    suspend fun signIn(context: Context): Result<GoogleSignInTokens> = runCatching {
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = sha256(rawNonce)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credential = CredentialManager.create(context)
            .getCredential(context, request)
            .credential

        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type returned by Credential Manager"
        }
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

        GoogleSignInTokens(idToken = googleIdTokenCredential.idToken, rawNonce = rawNonce)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
