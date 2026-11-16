package com.otpforwarder.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorTitle = intent.getStringExtra("error_title") ?: "Application Startup Error"
        val errorMessage = intent.getStringExtra("error_message") ?: "Unknown error"
        val stackTrace = intent.getStringExtra("stack_trace") ?: "No stack trace available"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
            setBackgroundColor(Color.WHITE)
        }

        val titleView = TextView(this).apply {
            text = errorTitle
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#D32F2F"))
        }
        layout.addView(titleView)

        val messageView = TextView(this).apply {
            text = errorMessage
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 16, 0, 16)
        }
        layout.addView(messageView)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }

        val copyButton = Button(this).apply {
            text = "Copy Error Details"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Crash Details", "Error: $errorMessage\n\nStack Trace:\n$stackTrace")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashActivity, "Error copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        }
        buttonLayout.addView(copyButton)

        val restartButton = Button(this).apply {
            text = "Restart App"
            setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(launchIntent)
                }
                finish()
            }
        }
        buttonLayout.addView(restartButton)
        layout.addView(buttonLayout)

        val scrollView = ScrollView(this).apply {
            setPadding(0, 16, 0, 0)
        }
        val traceView = TextView(this).apply {
            text = stackTrace
            textSize = 11f
            setTextIsSelectable(true)
            setTypeface(Typeface.MONOSPACE)
            setBackgroundColor(Color.parseColor("#F1F5F9"))
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(24, 24, 24, 24)
        }
        scrollView.addView(traceView)
        layout.addView(scrollView)

        setContentView(layout)
    }
}
