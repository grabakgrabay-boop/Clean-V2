package com.example.cleaner

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class JunkItem(
    val path: String,
    val category: String,
    val size: Long,
    val selected: Boolean = true
)

data class DupGroup(val files: List<File>, val size: Long)

data class DeleteResult(val count: Int, val freed: Long, val deleted: Set<String>)

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

/** Aturan keamanan: folder & file yang TIDAK PERNAH disentuh. */
object Safe {
    val root: File get() = Environment.getExternalStorageDirectory()

    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    // Folder yang dilewati total (data aplikasi, database, backup chat)
    private val protectedDirs = setOf("android", "databases", "backups", "backup")

    // Folder standar Android, tidak boleh dihapus walau kosong
    val standardDirs = setOf(
        "android", "download", "dcim", "pictures", "music", "movies", "documents",
        "alarms", "notifications", "ringtones", "podcasts", "audiobooks", "recordings"
    )

    fun walk(base: File = root): Sequence<File> =
        base.walkTopDown()
            .onEnter { d -> d == base || d.name.lowercase() !in protectedDirs }
            .filter { it != base }

    fun isOld(f: File, ms: Long) = System.currentTimeMillis() - f.lastModified() > ms

    fun isHidden(f: File): Boolean = try {
        f.relativeTo(root).path.split("/").any { it.startsWith(".") }
    } catch (e: Exception) { false }
}

object Scanner {

    /** Cache aplikasi ini + cache aplikasi lain (jika Android mengizinkan). */
    fun cache(ctx: Context): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { dir ->
            dir.walkTopDown().filter { it.isFile }
                .forEach { out += JunkItem(it.path, "Cache aplikasi ini", it.length()) }
        }
        val data = File(Safe.root, "Android/data")
        data.listFiles()?.forEach { pkgDir ->
            if (pkgDir.name == ctx.packageName) return@forEach
            File(pkgDir, "cache").walkTopDown()
                .filter { it.isFile && Safe.isOld(it, Safe.HOUR) }
                .forEach { out += JunkItem(it.path, "Cache aplikasi lain", it.length()) }
        }
        return out
    }

    /** File sampah: thumbnail, log, laporan error. */
    fun junk(): List<JunkItem> {
        val junkExt = setOf("log", "hprof", "dmp", "trace")
        val out = mutableListOf<JunkItem>()
        for (f in Safe.walk()) {
            try {
                if (!f.isFile) continue
                val n = f.name.lowercase()
                val ext = f.extension.lowercase()
                if (f.parentFile?.name == ".thumbnails" || n.startsWith("thumbdata")) {
                    out += JunkItem(f.path, "Thumbnail galeri", f.length())
                } else if (n.startsWith("tombstone_") && Safe.isOld(f, Safe.DAY)) {
                    out += JunkItem(f.path, "Laporan crash", f.length())
                } else if (ext in junkExt && Safe.isOld(f, Safe.DAY)) {
                    out += JunkItem(f.path, "File log & laporan error", f.length())
                }
            } catch (e: Exception) { }
        }
        return out
    }

    /** File sementara (minimal berumur 1 hari agar tidak mengganggu proses yang berjalan). */
    fun temp(): List<JunkItem> {
        val tempExt = setOf("tmp", "temp", "crdownload", "part", "partial", "download")
        val out = mutableListOf<JunkItem>()
        for (f in Safe.walk()) {
            try {
                if (!f.isFile || !Safe.isOld(f, Safe.DAY)) continue
                val ext = f.extension.lowercase()
                if (ext in tempExt) {
                    out += JunkItem(f.path, "File sementara (.$ext)", f.length())
                } else if (f.name.startsWith("~$") || f.name.endsWith("~")) {
                    out += JunkItem(f.path, "File kunci / cadangan editor", f.length())
                }
            } catch (e: Exception) { }
        }
        return out
    }

    /** File tidak berguna: APK installer, file kosong, folder kosong, cadangan lama. */
    fun useless(ctx: Context): List<JunkItem> {
        val pm = ctx.packageManager
        val out = mutableListOf<JunkItem>()
        for (f in Safe.walk()) {
            try {
                if (f.isFile) {
                    val ext = f.extension.lowercase()
                    if (ext == "apk") {
                        val info = pm.getPackageArchiveInfo(f.path, 0)
                        val installed = info != null && try {
                            pm.getPackageInfo(info.packageName, 0); true
                        } catch (e: PackageManager.NameNotFoundException) { false }
                        if (installed)
                            out += JunkItem(f.path, "APK installer (aplikasi sudah terpasang)", f.length())
                        else
                            out += JunkItem(f.path, "APK installer (BELUM terpasang, periksa dulu)", f.length(), false)
                    } else if (f.length() == 0L && f.name != ".nomedia" && Safe.isOld(f, Safe.DAY)) {
                        out += JunkItem(f.path, "File kosong (0 byte)", 0)
                    } else if (ext in setOf("bak", "old", "orig") && Safe.isOld(f, 30 * Safe.DAY)) {
                        out += JunkItem(f.path, "Cadangan lama >30 hari (periksa dulu)", f.length(), false)
                    }
                } else if (f.isDirectory &&
                    f.name.lowercase() !in Safe.standardDirs &&
                    Safe.isOld(f, Safe.DAY) &&
                    f.list()?.isEmpty() == true
                ) {
                    out += JunkItem(f.path, "Folder kosong", 0)
                }
            } catch (e: Exception) { }
        }
        return out
    }

    /**
     * Sampah sistem yang BISA dijangkau tanpa root: sisa perbaikan disk (LOST.DIR),
     * tempat sampah galeri. File inti sistem (/system, /data) tidak disentuh.
     */
    fun system(): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        for (f in Safe.walk()) {
            try {
                if (!f.isFile || f.name == ".nomedia") continue
                val parent = f.parentFile?.name ?: ""
                val inLost = f.path.contains("/LOST.DIR/") || parent.equals("found.000", true)
                if (inLost) {
                    out += JunkItem(f.path, "Sisa perbaikan disk (LOST.DIR)", f.length())
                } else if (f.name.startsWith(".trashed-")) {
                    out += JunkItem(f.path, "Tempat sampah galeri/sistem", f.length(), Safe.isOld(f, 7 * Safe.DAY))
                } else if (parent.equals(".Trash", true) || parent.equals(".recycle", true)) {
                    out += JunkItem(f.path, "Tempat sampah aplikasi", f.length(), Safe.isOld(f, 30 * Safe.DAY))
                }
            } catch (e: Exception) { }
        }
        return out
    }

    /** Cari file duplikat (isi sama persis). */
    fun duplicates(): List<DupGroup> {
        val candidates = Safe.walk()
            .filter { it.isFile && it.length() >= 10 * 1024 && !Safe.isHidden(it) }
            .groupBy { it.length() }
            .filter { it.value.size > 1 }

        val groups = mutableListOf<DupGroup>()
        for ((size, files) in candidates) {
            val byPartial = files.groupBy { hash(it, 256 * 1024L) }
                .filter { it.key != null && it.value.size > 1 }
            for (pf in byPartial.values) {
                val full = pf.groupBy { hash(it, Long.MAX_VALUE) }
                    .filter { it.key != null && it.value.size > 1 }
                full.values.forEach { same ->
                    groups += DupGroup(same.sortedBy { f -> f.lastModified() }, size)
                }
            }
        }
        return groups.sortedByDescending { it.size * (it.files.size - 1) }
    }

    private fun hash(f: File, limit: Long): String? = try {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(f).use { ins ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (total < limit) {
                val want = minOf(buf.size.toLong(), limit - total).toInt()
                val r = ins.read(buf, 0, want)
                if (r < 0) break
                md.update(buf, 0, r)
                total += r
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { null }
}

object Cleaner {
    /**
     * Penghapusan aman: hanya File.delete() (tidak pernah rekursif),
     * hanya di penyimpanan bersama / cache aplikasi, tidak pernah menyentuh
     * Android/data selain folder "cache", dan tidak menghapus .nomedia.
     */
    fun delete(ctx: Context, paths: List<String>): DeleteResult {
        var count = 0
        var freed = 0L
        val deleted = mutableSetOf<String>()
        val rootPath = Safe.root.canonicalPath
        val own = listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).map { it.canonicalPath }

        for (p in paths) {
            try {
                val f = File(p)
                if (!f.exists() || f.name == ".nomedia") continue
                val cp = f.canonicalPath
                val allowed = cp.startsWith("$rootPath/") || own.any { cp.startsWith("$it/") }
                if (!allowed) continue
                if (cp.startsWith("$rootPath/Android/") && !cp.contains("/cache/")) continue
                if (f.isDirectory && !f.list().isNullOrEmpty()) continue
                val size = if (f.isFile) f.length() else 0L
                if (f.delete()) {
                    count++
                    freed += size
                    deleted += p
                }
            } catch (e: Exception) { }
        }
        if (deleted.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(ctx, deleted.toTypedArray(), null, null)
            } catch (e: Exception) { }
        }
        return DeleteResult(count, freed, deleted)
    }
}
