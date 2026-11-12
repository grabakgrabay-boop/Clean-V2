package com.example.cleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(TealDark).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("←", color = Color.White, fontSize = 24.sp) }
        }
        Text(
            title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = if (onBack == null) 12.dp else 0.dp)
        )
    }
}

@Composable
fun PermissionBanner(onRequest: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Izin akses file belum diberikan", fontWeight = FontWeight.Bold)
            Text("Aplikasi butuh izin agar bisa memindai penyimpanan.", fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequest) { Text("Berikan Izin") }
        }
    }
}

@Composable
fun InfoCard(emoji: String, text: String) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 13.sp)
        }
    }
}

@Composable
fun BottomActionBar(info: String, enabled: Boolean, buttonText: String, onClick: () -> Unit) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(info, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Button(
                onClick = onClick, enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Danger)
            ) { Text(buttonText) }
        }
    }
}
