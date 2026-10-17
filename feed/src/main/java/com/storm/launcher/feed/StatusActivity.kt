package com.storm.launcher.feed

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * What Storm Feed is for, and whether it's doing its job: Storm and the Google app installed, and Storm connected to the
 * feed through it. Opening Storm is one tap away.
 */
class StatusActivity : Activity() {

    private lateinit var status: LinearLayout
    private lateinit var openStorm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        column.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
        }, LinearLayout.LayoutParams(dp(96), dp(96)).apply { topMargin = dp(48) })
        column.addView(text(getString(R.string.app_name), 28f, bold = true), wrap(top = 16))
        column.addView(text(getString(R.string.about), 16f).apply { alpha = 0.8f }, fill(top = 12))
        column.addView(text(getString(R.string.status_title), 14f, bold = true).apply {
            alpha = 0.6f
            isAllCaps = true
        }, fill(top = 32))
        status = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(status, fill(top = 8))
        openStorm = Button(this).apply {
            text = getString(R.string.open_storm)
            setOnClickListener { stormLaunchIntent()?.let(::startActivity) }
        }
        column.addView(openStorm, wrap(top = 32))

        val scroll = ScrollView(this).apply {
            addView(column)
            setPadding(dp(24), 0, dp(24), 0)
            clipToPadding = false
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(dp(24) + bars.left, bars.top, dp(24) + bars.right, bars.bottom + dp(24))
                insets
            }
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        showStatus()
    }

    private fun showStatus() {
        status.removeAllViews()
        val stormInstalled = stormLaunchIntent() != null
        val googleInstalled = isInstalled(GOOGLE_APP_PACKAGE)
        val connected = FeedBridgeService.connectedClients.get() > 0
        line(stormInstalled, if (stormInstalled) R.string.status_storm_installed else R.string.status_storm_missing)
        line(googleInstalled, if (googleInstalled) R.string.status_google_installed else R.string.status_google_missing)
        if (stormInstalled && googleInstalled) {
            line(connected, if (connected) R.string.status_connected else R.string.status_not_connected)
        }
        openStorm.visibility = if (stormInstalled) View.VISIBLE else View.GONE
    }

    private fun line(ok: Boolean, message: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(text(if (ok) "✓" else "!", 18f, bold = true).apply {
            setTextColor(if (ok) 0xFF1E8E3E.toInt() else 0xFFD93025.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(text(getString(message), 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        status.addView(row, fill(top = 8))
    }

    private fun stormLaunchIntent(): Intent? = STORM_PACKAGES.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun text(value: String, sizeSp: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun wrap(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun fill(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        val STORM_PACKAGES = listOf("com.storm.launcher", "com.storm.launcher.debug")
    }
}
