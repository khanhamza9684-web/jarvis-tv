package com.jarvis.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Cycles through the bundled wallpapers. If you push your own image to
 *   /sdcard/Android/data/com.jarvis.launcher/files/Pictures/wallpaper.jpg   (or .png)
 * it becomes the first wallpaper in the cycle.
 */
class WallpaperProvider(private val context: Context) {

    private val prefs = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    private val bundled = intArrayOf(
        R.drawable.wallpaper_dusk,
        R.drawable.wallpaper_aurora,
        R.drawable.wallpaper_nebula
    )

    private fun customFile(): File? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        return listOf("wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png")
            .map { File(dir, it) }
            .firstOrNull { it.exists() }
    }

    private fun count(): Int = bundled.size + if (customFile() != null) 1 else 0

    fun current(): Drawable = drawableAt(prefs.getInt("index", 0).mod(count()))

    fun next(): Drawable {
        val idx = (prefs.getInt("index", 0) + 1).mod(count())
        prefs.edit().putInt("index", idx).apply()
        return drawableAt(idx)
    }

    private fun drawableAt(index: Int): Drawable {
        val custom = customFile()
        if (custom != null) {
            if (index == 0) {
                decode(custom)?.let { return it }
            } else {
                return bundledAt(index - 1)
            }
        }
        return bundledAt(if (custom != null) 0 else index)
    }

    private fun bundledAt(i: Int): Drawable =
        ContextCompat.getDrawable(context, bundled[i.mod(bundled.size)])!!

    private fun decode(file: File): Drawable? = try {
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
    }
}
