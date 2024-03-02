package com.example.operationtimeoutsample

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity(), TimeoutListener {
    // TAG for logging
    private val tag = "MainActivity"

    // Views
    private lateinit var textView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById<TextView>(R.id.textView)
        // リスナ設定
        // スタートボタン
        val startButton = findViewById<MaterialButton>(R.id.startButton)
        startButton.setOnClickListener {
            TimeoutManager.initialize(this, this, 1)
            textView.text = "Timer started."
        }
        // ストップボタン
        val stopButton = findViewById<MaterialButton>(R.id.stopButton)
        stopButton.setOnClickListener {
            TimeoutManager.stopTimer()
            textView.text = "Timer stopped."
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            Log.d(tag, "onTouchEvent: touched screen.")
            TimeoutManager.userDidAction()
        }
        return super.onTouchEvent(event)
    }

    /**
     * タイムアウト時処理
     */
    override fun onTimeout() {
        runOnUiThread {
            textView.text = "Timer stopped."
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("No operation timeout occurred. Please retry.")
                .setPositiveButton("OK") { _, _ -> }
                .show()
        }
    }

    /**
     * 1秒ごとのカウント
     */
    override fun tick(second: Int) {
        runOnUiThread { textView.text = "foreground count: ${second.toString()}" }
    }
}
