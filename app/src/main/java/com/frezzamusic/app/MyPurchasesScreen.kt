package com.frezzamusic.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frezzamusic.app.data.PurchasedAudioStorage
import com.frezzamusic.app.data.PurchasedDownloadsApi
import com.frezzamusic.app.data.PurchaseIdentity
import com.frezzamusic.app.model.Track
import kotlinx.coroutines.launch

/**
 * Purchases are never inferred from local flags. A configured authenticated
 * API is required to show entitlements or download purchased tracks.
 */
@Composable
fun MyPurchasesScreen(
    catalog: List<Track>,
    api: PurchasedDownloadsApi?,
    storage: PurchasedAudioStorage?,
    identity: PurchaseIdentity?,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var ids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var signedInEmail by remember { mutableStateOf(identity?.email().orEmpty()) }
    var loginEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var activeDownload by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    fun refresh() {
        if (api == null) return
        loading = true
        error = null
        scope.launch {
            runCatching { api.purchasedTrackIds() }
                .onSuccess { ids = it }
                .onFailure { ids = emptySet(); error = it.message ?: "Não foi possível consultar suas compras" }
            loading = false
        }
    }
    LaunchedEffect(api, signedInEmail) { if (signedInEmail.isNotBlank()) refresh() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Minhas Compras", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text("Voltar") }
        }
        Text("Ouça gratuitamente. Compras permitem baixar as músicas, sem diferença na qualidade do áudio.")
        Spacer(Modifier.height(12.dp))
        if (identity != null && identity.available && signedInEmail.isBlank()) {
            OutlinedTextField(value = loginEmail, onValueChange = { loginEmail = it },
                label = { Text("E-mail") }, singleLine = true)
            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text("Senha") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Row {
                Button(onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        runCatching {
                            if (registering) identity.register(loginEmail, password)
                            else identity.signIn(loginEmail, password)
                        }.onSuccess {
                            signedInEmail = identity.email().orEmpty()
                            password = ""
                        }.onFailure { error = it.message ?: "Erro ao entrar" }
                        loading = false
                    }
                }, enabled = !loading) { Text(if (registering) "Criar conta" else "Entrar") }
                TextButton(onClick = { registering = !registering }) {
                    Text(if (registering) "Já tenho conta" else "Criar conta")
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        } else if (api == null || storage == null || identity == null || !identity.available) {
            Text("A conta e o servidor de compras ainda não estão configurados neste aplicativo. Nenhuma compra ou download pago pode ser realizado.", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Conta: $signedInEmail")
            TextButton(onClick = {
                identity.signOut()
                signedInEmail = ""
                ids = emptySet()
                error = null
            }) { Text("Sair") }
            OutlinedButton(onClick = { refresh() }, enabled = !loading) { Text("Atualizar compras") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            val tracks = catalog.filter { it.id in ids }.distinctBy { it.id }
            if (!loading && error == null && tracks.isEmpty()) Text("Nenhuma faixa comprada encontrada nesta conta e neste catálogo.")
            LazyColumn {
                items(tracks, key = { it.id }) { track ->
                    val local = remember(track.id, revision) { storage.localFile(track.id) }
                    ListItem(
                        headlineContent = { Text(track.title) },
                        supportingContent = { Text(track.artist + if (local != null) " • Baixada" else " • Disponível para baixar") },
                        trailingContent = {
                            if (activeDownload == track.id) CircularProgressIndicator(Modifier.size(24.dp))
                            else if (local != null) TextButton(onClick = {
                                storage.remove(track.id)
                                revision++
                            }) { Text("Remover") }
                            else Button(onClick = {
                                activeDownload = track.id
                                error = null
                                scope.launch {
                                    storage.downloadPurchasedTrack(track.id)
                                        .onSuccess { revision++ }
                                        .onFailure { error = it.message ?: "Falha no download" }
                                    activeDownload = null
                                }
                            }, enabled = activeDownload == null) { Text("Baixar") }
                        }
                    )
                }
            }
        }
    }
}
