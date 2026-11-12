package com.example.cleaner

import android.widget.Toast
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
fun DuplicateScreen(hasAccess: Boolean, onRequestAccess: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(emptyList<DupGroup>()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(true) }
    var scanned by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    fun runScan() {
        busy = true
        scope.launch {
            val g = withContext(Dispatchers.IO) { Scanner.duplicates() }
            groups = g
            // Salinan tertua dianggap asli dan TIDAK dipilih
            selected = g.flatMap { it.files.drop(1) }.map { it.path }.toSet()
            scanned = true
            busy = false
        }
    }
    LaunchedEffect(hasAccess) { runScan() }

    fun toggle(g: DupGroup, path: String) {
        if (path in selected) {
            selected = selected - path
        } else {
            val after = selected + path
            if (g.files.all { it.path in after }) {
                Toast.makeText(ctx, "Minimal satu salinan harus dipertahankan", Toast.LENGTH_SHORT).show()
            } else {
                selected = after
            }
        }
    }

    val selSize = groups.sumOf { g -> g.files.count { it.path in selected }.toLong() * g.size }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar("Pencari Data Duplikat", onBack)
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
                    Text("Membandingkan isi file… (bisa agak lama)")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        InfoCard(
                            "📑",
                            "File dengan isi persis sama dikelompokkan. Salinan tertua ditandai ASLI dan " +
                                "tidak dipilih. Minimal satu salinan selalu dipertahankan."
                        )
                    }
                    message?.let { m ->
                        item { Text(m, Modifier.padding(16.dp), color = TealDark, fontWeight = FontWeight.Bold) }
                    }
                    if (scanned && groups.isEmpty()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("✨", fontSize = 56.sp)
                                Text("Tidak ada file duplikat.", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    groups.forEachIndexed { gi, g ->
                        item(key = "g$gi") {
                            Text(
                                "${g.files.size} salinan • ${formatSize(g.size)} per file",
                                Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp),
                                fontWeight = FontWeight.Bold, color = TealDark
                            )
                        }
                        items(g.files, key = { it.path }) { f ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { toggle(g, f.path) }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = f.path in selected, onCheckedChange = { toggle(g, f.path) })
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, maxLines = 1, fontSize = 14.sp)
                                    Text(f.parent ?: "", maxLines = 1, fontSize = 11.sp, color = Color.Gray)
                                }
                                if (f == g.files.first()) {
                                    Text("ASLI", fontSize = 11.sp, color = TealDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!busy && groups.isNotEmpty()) {
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
            title = { Text("Hapus ${selected.size} duplikat?") },
            text = { Text("Total ${formatSize(selSize)}. Permanen dan tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    val paths = selected.toList()
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { Cleaner.delete(ctx, paths) }
                        message = "✅ ${res.count} duplikat dihapus, ${formatSize(res.freed)} dibebaskan"
                        groups = groups
                            .map { DupGroup(it.files.filter { f -> f.path !in res.deleted }, it.size) }
                            .filter { it.files.size > 1 }
                        selected = selected - res.deleted
                        busy = false
                    }
                }) { Text("Hapus", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } }
        )
    }
}
