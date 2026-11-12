package com.example.cleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CleanScreen(
    title: String,
    emoji: String,
    description: String,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onBack: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
    scan: suspend () -> List<JunkItem>
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(emptyList<JunkItem>()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(true) }
    var scanned by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    fun runScan() {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { scan() }
            items = r.sortedByDescending { it.size }
            selected = r.filter { it.selected }.map { it.path }.toSet()
            scanned = true
            busy = false
        }
    }
    LaunchedEffect(hasAccess) { runScan() }

    val selSize = items.filter { it.path in selected }.sumOf { it.size }
    val allSelected = items.isNotEmpty() && items.all { it.path in selected }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title, onBack)
        if (!hasAccess) PermissionBanner(onRequestAccess)

        Box(Modifier.weight(1f)) {
            if (busy) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Memindai…")
                }
            } else {
                val grouped = items.groupBy { it.category }
                LazyColumn(Modifier.fillMaxSize()) {
                    item { InfoCard(emoji, description) }
                    if (extra != null) item { Box(Modifier.padding(horizontal = 12.dp)) { extra() } }
                    message?.let { m ->
                        item {
                            Text(m, Modifier.padding(16.dp), color = TealDark, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (scanned && items.isEmpty()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("✨", fontSize = 56.sp)
                                Text("Bersih! Tidak ada yang perlu dihapus.", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    grouped.forEach { (cat, list) ->
                        item(key = "h_$cat") {
                            val allCat = list.all { it.path in selected }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(cat, fontWeight = FontWeight.Bold, color = TealDark)
                                    Text("${list.size} item • ${formatSize(list.sumOf { it.size })}", fontSize = 12.sp)
                                }
                                Checkbox(checked = allCat, onCheckedChange = { c ->
                                    val paths = list.map { it.path }
                                    selected = if (c) selected + paths else selected - paths
                                })
                            }
                        }
                        items(list, key = { it.path }) { item ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        selected = if (item.path in selected) selected - item.path else selected + item.path
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = item.path in selected, onCheckedChange = { c ->
                                    selected = if (c) selected + item.path else selected - item.path
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(item.file.name, maxLines = 1, fontSize = 14.sp)
                                    Text(item.file.parent ?: "", maxLines = 1, fontSize = 11.sp, color = Color.Gray)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(formatSize(item.size), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (!busy && items.isNotEmpty()) {
            Surface(color = Color.White) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = allSelected, onCheckedChange = { c ->
                        selected = if (c) items.map { it.path }.toSet() else emptySet()
                    })
                    Text("Pilih semua", fontSize = 14.sp)
                }
            }
            BottomActionBar(
                info = "${selected.size} dipilih • ${formatSize(selSize)}",
                enabled = selected.isNotEmpty(),
                buttonText = "Hapus"
            ) { confirm = true }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Hapus ${selected.size} item?") },
            text = { Text("Total ${formatSize(selSize)}. Penghapusan bersifat permanen dan tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    val paths = selected.toList()
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { Cleaner.delete(ctx, paths) }
                        message = "✅ ${res.count} item dihapus, ${formatSize(res.freed)} dibebaskan"
                        items = items.filter { it.path !in res.deleted }
                        selected = selected - res.deleted
                        busy = false
                    }
                }) { Text("Hapus", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } }
        )
    }
}
