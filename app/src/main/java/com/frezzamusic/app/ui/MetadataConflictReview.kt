package com.frezzamusic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frezzamusic.app.data.MetadataConflictRepository

/**
 * User-facing, field-by-field reconciliation.
 * The caller refreshes the library after applying source values.
 */
@Composable
fun MetadataConflictReview(
    repository: MetadataConflictRepository,
    onClose: () -> Unit,
    onApplied: () -> Unit
) {
    var revision by remember { mutableIntStateOf(0) }
    val pending = remember(revision) { repository.pending() }
    var position by remember { mutableIntStateOf(0) }
    var confirmAll by remember { mutableStateOf(false) }
    val current = pending.getOrNull(position.coerceIn(0, (pending.size - 1).coerceAtLeast(0)))
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ver alterações", style = MaterialTheme.typography.headlineMedium)
        if (current == null) {
            Text("Nenhuma divergência pendente.")
            Button(onClick = onClose) { Text("Voltar") }
        } else {
            Text("Divergência ${position + 1} de ${pending.size}")
            Text("Item: ${current.entityId}", style = MaterialTheme.typography.titleMedium)
            Text("Campo: ${current.field}")
            Text("Minha edição: ${current.userValue}")
            Text("Nova fonte: ${current.incomingSource ?: "(vazio)"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    repository.decide(current.entityId, current.field, false)
                    position = 0
                    revision++
                }) { Text("Manter edição") }
                Button(onClick = {
                    repository.decide(current.entityId, current.field, true)
                    position = 0
                    revision++
                    onApplied()
                }) { Text("Sobrescrever") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { position = (position - 1).coerceAtLeast(0) },
                    enabled = position > 0) { Text("Anterior") }
                OutlinedButton(onClick = { position = (position + 1).coerceAtMost(pending.lastIndex) },
                    enabled = position < pending.lastIndex) { Text("Próxima") }
            }
            Button(onClick = { confirmAll = true }) { Text("Sobrescrever todas") }
            TextButton(onClick = onClose) { Text("Voltar sem decidir") }
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Confirmar sobrescrita?") },
            text = { Text("As informações das fontes substituirão todas as edições divergentes pendentes.") },
            confirmButton = {
                Button(onClick = {
                    repository.overwriteAll()
                    confirmAll = false
                    onApplied()
                    onClose()
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancelar") } }
        )
    }
}
