package com.jarvis.launcher

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Jarvis.Host {

    private lateinit var repo: AppRepository
    private lateinit var wallpapers: WallpaperProvider
    private lateinit var jarvis: Jarvis
    private lateinit var adapter: AppAdapter

    private lateinit var appRow: RecyclerView
    private lateinit var wallpaperContainer: View
    private lateinit var wallpaperBack: ImageView
    private lateinit var wallpaperFront: ImageView
    private lateinit var greeting: TextView
    private lateinit var dateView: TextView
    private lateinit var jarvisButton: View
    private lateinit var jarvisLabel: TextView
    private lateinit var jarvisOrb: View
    private lateinit var caption: TextView

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var orbAnimator: ObjectAnimator? = null
    private var driftAnimator: ObjectAnimator? = null

    private val tick = object : Runnable {
        override fun run() {
            updateGreeting()
            main.postDelayed(this, 30_000)
        }
    }

    private val hideCaption = Runnable { caption.animate().alpha(0f).setDuration(400).start() }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = AppRepository(this)
        wallpapers = WallpaperProvider(this)

        appRow = findViewById(R.id.appRow)
        wallpaperContainer = findViewById(R.id.wallpaperContainer)
        wallpaperBack = findViewById(R.id.wallpaperBack)
        wallpaperFront = findViewById(R.id.wallpaperFront)
        greeting = findViewById(R.id.greeting)
        dateView = findViewById(R.id.dateView)
        jarvisButton = findViewById(R.id.jarvisButton)
        jarvisLabel = findViewById(R.id.jarvisLabel)
        jarvisOrb = findViewById(R.id.jarvisOrb)
        caption = findViewById(R.id.jarvisCaption)

        adapter = AppAdapter(::launch, ::showAppInfo, ::centerOn)
        appRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        appRow.adapter = adapter

        wallpaperBack.setImageDrawable(wallpapers.current())

        jarvis = Jarvis(this, this)
        jarvisButton.setOnClickListener { jarvis.startListening() }
        jarvisButton.setOnFocusChangeListener { _, focused ->
            jarvisLabel.setTextColor(if (focused) Color.BLACK else Color.WHITE)
        }

        // Back never leaves the launcher; it just returns to the first app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        loadApps()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        main.post(tick)
        startDrift()
    }

    override fun onPause() {
        main.removeCallbacks(tick)
        driftAnimator?.cancel()
        super.onPause()
    }

    override fun onStop() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
            // not registered
        }
        jarvis.stop()
        super.onStop()
    }

    override fun onDestroy() {
        jarvis.shutdown()
        io.shutdown()
        super.onDestroy()
    }

    /** Pressing Home while already on the launcher brings us back to the start. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) goHome()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST -> {
                jarvis.startListening()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Jarvis.REQ_AUDIO) {
            jarvis.onPermissionResult(
                grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            )
        }
    }

    // ------------------------------------------------------------------ apps

    private fun loadApps() {
        io.execute {
            val apps = repo.loadApps()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                adapter.submit(apps)
                appRow.post { if (currentFocus == null) focusFirstApp() }
            }
        }
    }

    private fun launch(app: AppEntry) {
        try {
            repo.recordLaunch(app.packageName)
            startActivity(app.newLaunchIntent())
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Long-press an app tile to open its system info page (uninstall, permissions...). */
    private fun showAppInfo(app: AppEntry) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "App info isn't available on this TV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun focusFirstApp() {
        appRow.scrollToPosition(0)
        appRow.post { appRow.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    /** Keep the focused tile in the middle of the screen, like tvOS. */
    private fun centerOn(view: View) {
        val dx = (view.left + view.width / 2) - appRow.width / 2
        if (dx != 0) appRow.smoothScrollBy(dx, 0)
    }

    // ------------------------------------------------------------------ greeting + wallpaper

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
        greeting.text = if (Config.OWNER_NAME.isBlank()) part else "$part, ${Config.OWNER_NAME}"
        dateView.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
    }

    private fun startDrift() {
        val anim = driftAnimator ?: ObjectAnimator.ofPropertyValuesHolder(
            wallpaperContainer,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.08f)
        ).apply {
            duration = 40_000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = LinearInterpolator()
        }.also { driftAnimator = it }
        if (!anim.isStarted) anim.start()
    }

    private fun crossfadeTo(drawable: Drawable) {
        if (wallpaperFront.alpha > 0f) {
            wallpaperFront.drawable?.let { wallpaperBack.setImageDrawable(it) }
        }
        wallpaperFront.animate().cancel()
        wallpaperFront.setImageDrawable(drawable)
        wallpaperFront.alpha = 0f
        wallpaperFront.animate().alpha(1f).setDuration(700).withEndAction {
            wallpaperBack.setImageDrawable(drawable)
            wallpaperFront.alpha = 0f
        }.start()
    }

    // ------------------------------------------------------------------ Jarvis.Host

    override fun onStateChanged(state: Jarvis.State, text: String?) {
        orbAnimator?.cancel()
        jarvisOrb.scaleX = 1f
        jarvisOrb.scaleY = 1f
        jarvisOrb.alpha = 1f

        when (state) {
            Jarvis.State.LISTENING -> {
                pulseOrb(1.45f, 600)
                showCaption(text ?: getString(R.string.listening))
            }
            Jarvis.State.THINKING -> {
                pulseOrb(1.15f, 350)
                if (text != null) showCaption(text)
            }
            Jarvis.State.SPEAKING -> {
                pulseOrb(1.25f, 420)
                if (text != null) showCaption(text)
            }
            Jarvis.State.IDLE -> {
                main.removeCallbacks(hideCaption)
                main.postDelayed(hideCaption, 3500)
            }
        }
    }

    private fun showCaption(text: String) {
        main.removeCallbacks(hideCaption)
        caption.text = text
        caption.animate().alpha(1f).setDuration(200).start()
    }

    private fun pulseOrb(scale: Float, durationMs: Long) {
        orbAnimator = ObjectAnimator.ofPropertyValuesHolder(
            jarvisOrb,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, scale),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, scale)
        ).apply {
            duration = durationMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    override fun findApp(query: String): AppEntry? {
        fun norm(s: String) = s.lowercase(Locale.getDefault()).filter { it.isLetterOrDigit() }
        val q = norm(query)
        if (q.isEmpty()) return null
        val apps = adapter.items()
        return apps.firstOrNull { norm(it.label) == q }
            ?: apps.firstOrNull { norm(it.label).startsWith(q) }
            ?: apps.firstOrNull { norm(it.label).contains(q) }
            ?: apps.firstOrNull { norm(it.label).length >= 4 && q.contains(norm(it.label)) }
    }

    override fun openApp(app: AppEntry) = launch(app)

    override fun nextWallpaper() {
        crossfadeTo(wallpapers.next())
    }

    override fun goHome() {
        focusFirstApp()
    }
}
