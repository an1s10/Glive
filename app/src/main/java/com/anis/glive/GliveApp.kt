package com.anis.glive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.anis.glive.data.Prefs
import com.anis.glive.data.Repository
import com.anis.glive.ui.Themes
import java.io.File

class GliveApp : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashHandler()
        prefs = Prefs(this)
        Themes.select(prefs.theme)
        repo = Repository(this, prefs)
        repo.start()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val text = buildString {
                    append("Glive ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n")
                    append("Thread ${t.name}\n\n")
                    append(e.stackTraceToString().take(6000))
                }
                crashFile().writeText(text)
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun crashFile() = File(filesDir, "last_crash.txt")

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(220)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(200L * 1024 * 1024).build() }
            .build()

    companion object {
        lateinit var instance: GliveApp
            private set
    }
}
