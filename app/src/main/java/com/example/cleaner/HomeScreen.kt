package com.example.cleaner

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class MenuItem(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val screen: Screen
)

private val menus = listOf(
    MenuItem("🧹", "Pembersih Cache", "Cache aplikasi", Color(0xFF26A69A), Screen.CACHE),
    MenuItem("🗑️", "Pembersih Sampah", "Thumbnail, log, error", Color(0xFFEF5350), Screen.JUNK),
    MenuItem("⏳", "File Sementara", ".tmp, unduhan gagal", Color(0xFFFFA726), Screen.TEMP),
    MenuItem("📄", "File Tidak Berguna", "APK, file & folder kosong", Color(0xFF7E57C2), Screen.USELESS),
    MenuItem("⚙️", "File Sistem", "Sisa disk & tempat sampah", Color(0xFF42A5F5), Screen.SYSTEM),
    MenuItem("📑", "Data Duplikat", "Cari file kembar", Color(0xFF26C6DA), Screen.DUPLICATES),
    MenuItem("📱", "Pengelola Aplikasi", "Lihat, tersembunyi, uninstal", Color(0xFF66BB6A), Screen.APPS)
)

private fun storageInfo(): Pair<Long, Long> = try {
    val st = StatFs(Environment.getExternalStorageDirectory().path)
    Pair(st.totalBytes, st.availableBytes)
} catch (e: Exception) { Pair(0L, 0L) }

@Composable
fun HomeScreen(hasAccess: Boolean, tick: Int, onRequest: () -> Unit, onOpen: (Screen) -> Unit) {
    val info = remember(tick) { storageInfo() }
    val total = info.first
    val free = info.second
    val used = total - free
    val frac = if (total > 0) used.toFloat() / total else 0f

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(TealDark, Teal)))
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(110.dp)) {
                        val stroke = 12.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(stroke / 2, stroke / 2)
                        drawArc(
                            color = Color.White.copy(alpha = 0.25f),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Text("${(frac * 100).toInt()}%", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("Pembersih Sampah", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Terpakai: ${formatSize(used)}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    Text("Tersedia: ${formatSize(free)}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    Text("Total: ${formatSize(total)}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }

        if (!hasAccess) PermissionBanner(onRequest)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(menus) { m ->
                Card(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onOpen(m.screen) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(m.color.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) { Text(m.emoji, fontSize = 24.sp) }
                        Spacer(Modifier.height(12.dp))
                        Text(m.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(m.subtitle, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
