package com.jarvis.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Cycles through your own wallpapers (newest first) and then the bundled ones.
 * Your wallpapers are added from the phone-upload page (see UploadServer).
 */
class WallpaperProvider(private val context: Context) {

    private val prefs = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    private val bundled = intArrayOf(
        R.drawable.wallpaper_dusk,
        R.drawable.wallpaper_aurora,
        R.drawable.wallpaper_nebula
    )

    private fun dir(): File = File(context.filesDir, "wallpapers").apply { mkdirs() }

    private fun customFiles(): List<File> =
        dir().listFiles { f -> f.isFile && f.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    private fun count(): Int = bundled.size + customFiles().size

    fun current(): Drawable = drawableAt(prefs.getInt("index", 0).mod(count()))

    fun next(): Drawable {
        val idx = (prefs.getInt("index", 0) + 1).mod(count())
        prefs.edit().putInt("index", idx).apply()
        return drawableAt(idx)
    }

    /** Jump to the most recently added wallpaper. */
    fun showNewest(): Drawable {
        prefs.edit().putInt("index", 0).apply()
        return current()
    }

    /** Saves an uploaded image. Returns false if it isn't a readable image. */
    @Synchronized
    fun saveFrom(input: InputStream): Boolean {
        val d = dir()
        val tmp = File(d, "incoming.tmp")
        return try {
            tmp.outputStream().use { input.copyTo(it) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                tmp.delete()
                false
            } else {
                val dest = File(d, "wp_${System.currentTimeMillis()}.jpg")
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    false
                } else {
                    customFiles().drop(MAX_CUSTOM).forEach { it.delete() }
                    true
                }
            }
        } catch (e: IOException) {
            tmp.delete()
            false
        }
    }

    @Synchronized
    fun clearCustom() {
        customFiles().forEach { it.delete() }
        prefs.edit().putInt("index", 0).apply()
    }

    private fun drawableAt(index: Int): Drawable {
        val custom = customFiles()
        if (index < custom.size) {
            decode(custom[index])?.let { return it }
            return bundledAt(0)
        }
        return bundledAt(index - custom.size)
    }

    private fun bundledAt(i: Int): Drawable =
        ContextCompat.getDrawable(context, bundled[i.mod(bundled.size)])!!

    private fun decode(file: File): Drawable? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.path, opts)?.let { BitmapDrawable(context.resources, it) }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        const val MAX_CUSTOM = 10
    }
}
