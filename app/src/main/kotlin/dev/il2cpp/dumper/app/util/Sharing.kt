package dev.il2cpp.dumper.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Sharing {
    private fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareFile(context: Context, file: File, mime: String = "text/plain") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(context: Context, text: String, label: String) {
        val cache = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(cache, label)
        file.writeText(text)
        shareFile(context, file, "text/plain")
    }

    /** Zips the whole dump directory (excluding meta.json) and shares it. */
    fun shareDumpZip(context: Context, dir: File, zipName: String) {
        val cache = File(context.cacheDir, "share").apply { mkdirs() }
        val zip = File(cache, zipName)
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            dir.walkTopDown().filter { it.isFile && it.name != "meta.json" }.forEach { f ->
                val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                zos.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        shareFile(context, zip, "application/zip")
    }

    /** Lets the user save a file to a location they choose (handled by the Activity result launcher). */
    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "")) {
        "cs", "h", "py", "txt", "js", "ts" -> "text/plain"
        "json" -> "application/json"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
