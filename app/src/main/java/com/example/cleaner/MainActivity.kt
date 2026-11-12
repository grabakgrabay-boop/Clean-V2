package com.example.cleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

enum class Screen { HOME, CACHE, JUNK, TEMP, USELESS, SYSTEM, DUPLICATES, APPS }

class MainActivity : ComponentActivity() {

    private var tick by mutableIntStateOf(0)

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF00695C.toInt()
        setContent { CleanerTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    @Composable
    private fun Root() {
        var screen by remember { mutableStateOf(Screen.HOME) }
        val access = remember(tick) { hasAccess() }
        val ctx = this@MainActivity
        val back = { screen = Screen.HOME }
        val req = { requestAccess() }

        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

        when (screen) {
            Screen.HOME -> HomeScreen(access, tick, onRequest = req, onOpen = { screen = it })

            Screen.CACHE -> CleanScreen(
                title = "Pembersih Cache", emoji = "🧹",
                description = "Menghapus file cache. Cache aman dihapus: aplikasi akan membuatnya lagi saat dibutuhkan. " +
                    "Data, akun, dan pengaturan aplikasi TIDAK terhapus.",
                hasAccess = access, onRequestAccess = req, onBack = back,
                extra = {
                    OutlinedButton(onClick = {
                        startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                    }) { Text("Cache aplikasi lain tak terdeteksi? Buka Pengaturan Penyimpanan") }
                },
                scan = { Scanner.cache(ctx) }
            )

            Screen.JUNK -> CleanScreen(
                title = "Pembersih Sampah", emoji = "🗑️",
                description = "Thumbnail galeri, file log, dan laporan error. Semua dibuat ulang otomatis oleh sistem bila perlu.",
                hasAccess = access, onRequestAccess = req, onBack = back,
                scan = { Scanner.junk() }
            )

            Screen.TEMP -> CleanScreen(
                title = "Pembersih File Sementara", emoji = "⏳",
                description = "File .tmp, unduhan yang gagal, dan file kunci editor. Hanya file berumur lebih dari 1 hari.",
                hasAccess = access, onRequestAccess = req, onBack = back,
                scan = { Scanner.temp() }
            )

            Screen.USELESS -> CleanScreen(
                title = "File Tidak Berguna", emoji = "📄",
                description = "APK installer, file 0 byte, folder kosong, dan cadangan lama. Item bertanda 'periksa dulu' tidak dipilih otomatis.",
                hasAccess = access, onRequestAccess = req, onBack = back,
                scan = { Scanner.useless(ctx) }
            )

            Screen.SYSTEM -> CleanScreen(
                title = "Pembersih File Sistem", emoji = "⚙️",
                description = "Sampah sistem yang aman dijangkau tanpa root: sisa perbaikan disk (LOST.DIR) dan tempat sampah galeri. " +
                    "File inti sistem Android sengaja tidak disentuh.",
                hasAccess = access, onRequestAccess = req, onBack = back,
                scan = { Scanner.system() }
            )

            Screen.DUPLICATES -> DuplicateScreen(access, req, back)

            Screen.APPS -> AppsScreen(tick, back)
        }
    }
}
