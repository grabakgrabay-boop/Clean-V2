@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.cleaner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val pkg: String,
    val version: String,
    val system: Boolean,
    val enabled: Boolean,
    val hidden: Boolean
)

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    return pm.getInstalledPackages(0).mapNotNull { p ->
        val ai = p.applicationInfo ?: return@mapNotNull null
        val sys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val hasLauncher = pm.getLaunchIntentForPackage(p.packageName) != null
        AppEntry(
            label = ai.loadLabel(pm).toString(),
            pkg = p.packageName,
            version = p.versionName ?: "-",
            system = sys,
            enabled = ai.enabled,
            hidden = !hasLauncher || !ai.enabled
        )
    }.sortedBy { it.label.lowercase() }
}

@Composable
fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) { null }
        }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(44.dp))
    } else {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.LightGray))
    }
}

@Composable
fun AppsScreen(tick: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(tick) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
        loading = false
    }

    val tabs = listOf("Semua", "Pengguna", "Sistem", "Tersembunyi")
    fun inTab(a: AppEntry, t: Int) = when (t) {
        0 -> true
        1 -> !a.system
        2 -> a.system
        else -> a.hidden
    }
    val filtered = apps.filter {
        inTab(it, tab) && (query.isBlank() ||
            it.label.contains(query, true) || it.pkg.contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar("Pengelola Aplikasi", onBack)

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { i, name ->
                FilterChip(
                    selected = tab == i,
                    onClick = { tab = i },
                    label = { Text("$name (${apps.count { inTab(it, i) }})") }
                )
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Cari aplikasi…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )

        if (tab == 3) {
            Text(
                "Tersembunyi = tidak punya ikon di menu, atau dinonaktifkan.",
                fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.pkg }) { a ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a.pkg)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${a.pkg} • v${a.version}", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                val badges = buildList {
                                    if (a.system) add("Sistem")
                                    if (a.hidden) add("Tersembunyi")
                                    if (!a.enabled) add("Nonaktif")
                                }
                                if (badges.isNotEmpty())
                                    Text(badges.joinToString(" • "), fontSize = 11.sp, color = TealDark)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))
                                    )
                                }) { Text("Info") }
                                if (!a.system && a.pkg != ctx.packageName) {
                                    TextButton(onClick = { toRemove = a }) { Text("Hapus", color = Danger) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    toRemove?.let { app ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Uninstal ${app.label}?") },
            text = { Text("Aplikasi dan datanya akan dihapus dari perangkat. Android akan meminta konfirmasi terakhir.") },
            confirmButton = {
                TextButton(onClick = {
                    toRemove = null
                    ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                }) { Text("Uninstal", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Batal") } }
        )
    }
}
