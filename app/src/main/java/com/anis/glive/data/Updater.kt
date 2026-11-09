package com.anis.glive.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.anis.glive.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class Release(val version: Int, val name: String, val apkUrl: String)

object Updater {
    private const val API = "https://api.github.com/repos/an1s10/glive/releases/latest"
    private val http = OkHttpClient()

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(API).header("Accept", "application/vnd.github+json").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val o = json.parseToJsonElement(r.body!!.string()).obj() ?: return@use null
                val tag = o.str("tag_name")
                val version = tag.substringAfterLast('.').toIntOrNull() ?: return@use null
                val asset = o["assets"].arr()?.mapNotNull { it.obj() }?.firstOrNull { it.str("name").endsWith(".apk") }
                    ?: return@use null
                Release(version, o.str("name"), asset.str("browser_download_url"))
            }
        }.getOrNull()
    }

    fun isNewer(r: Release) = r.version > BuildConfig.VERSION_CODE

    suspend fun downloadAndInstall(context: Context, r: Release, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "Glive.apk")
            http.newCall(Request.Builder().url(r.apkUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false
                val body = resp.body ?: return@runCatching false
                val total = body.contentLength().toFloat()
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) onProgress(done / total)
                        }
                    }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            true
        }.getOrDefault(false)
    }
}
