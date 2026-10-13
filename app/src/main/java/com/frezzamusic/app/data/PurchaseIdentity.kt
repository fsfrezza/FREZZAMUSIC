package com.frezzamusic.app.data

import android.content.Context
import com.frezzamusic.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Shared Firebase project, with a distinct Firebase Android app ID per package/flavor. */
class PurchaseIdentity(context: Context) {
    private val firebaseApp: FirebaseApp?
    val available: Boolean
        get() = firebaseApp != null && BuildConfig.PURCHASE_API_URL.startsWith("https://")

    init {
        firebaseApp = if (BuildConfig.FIREBASE_API_KEY.isBlank() ||
            BuildConfig.FIREBASE_APP_ID.isBlank() || BuildConfig.FIREBASE_PROJECT_ID.isBlank()) null
        else {
            val name = "purchases-" + context.packageName
            FirebaseApp.getApps(context).firstOrNull { it.name == name }
                ?: FirebaseApp.initializeApp(context.applicationContext,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .build(), name)
        }
    }

    private fun auth(): FirebaseAuth = FirebaseAuth.getInstance(
        firebaseApp ?: throw IllegalStateException("Conta ainda não configurada neste aplicativo")
    )

    fun email(): String? = if (firebaseApp == null) null else auth().currentUser?.email

    fun userId(): String? = if (firebaseApp == null) null else auth().currentUser?.uid

    suspend fun signIn(email: String, password: String) {
        require(email.isNotBlank() && password.isNotBlank()) { "Informe e-mail e senha" }
        auth().signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String) {
        require(email.isNotBlank() && password.length >= 6) { "Informe e-mail e senha com pelo menos seis caracteres" }
        auth().createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun idToken(): String? = auth().currentUser?.getIdToken(false)?.await()?.token

    fun onAuthChanged(callback: () -> Unit): () -> Unit {
        if(firebaseApp==null)return {}
        val auth=auth()
        val listener=FirebaseAuth.AuthStateListener { callback() }
        auth.addAuthStateListener(listener)
        return { auth.removeAuthStateListener(listener) }
    }

    fun signOut() { if (firebaseApp != null) auth().signOut() }
}
