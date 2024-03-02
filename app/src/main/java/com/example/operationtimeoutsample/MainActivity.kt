package com.example.operationtimeoutsample

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import java.time.Duration
import java.time.LocalDateTime
import java.util.Timer
import kotlin.concurrent.*

class MainActivity : AppCompatActivity(), TimeoutListener {
    // TAG for logging
    private val TAG = "MainActivity"

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
            TimeoutManager.startTimer(this, this, 1)
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
            Log.d(TAG, "onTouchEvent: touched screen.")
            TimeoutManager.userDidAction()
        }
        return super.onTouchEvent(event)
    }

    override fun onStop() {
        super.onStop()
        TimeoutManager.appDidEnterBackground()
    }

    override fun onStart() {
        super.onStart()
        TimeoutManager.appWillEnterForeground()
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

/**
 * タイムアウトリスナーインターフェース
 */
interface TimeoutListener {
    fun onTimeout()

    fun tick(second: Int)
}

/**
 * タイムアウト管理シングルトンオブジェクト
 */
object TimeoutManager {
    private lateinit var activity: AppCompatActivity
    private var timer: Timer? = null
    private var timerForActivity: Timer? = null
    private lateinit var timeoutListener: TimeoutListener
    private var intervalMin: Int = 0
    private var lastActionTime: LocalDateTime? = null
    private var isTimerActive = false
    private const val TAG = "TimeoutManager"

    /**
     * 渡されたintervalMinを保存し、その時間でタイマーをスタートする。
     * （事前に動いているタイマーがあればストップする。）
     * スタート時に初回操作時刻を記録し、バックグラウンドタイムアウトチェックを有効にする。
     */
    fun startTimer(
        activity: AppCompatActivity,
        timeoutListener: TimeoutListener,
        intervalMin: Int
    ) {
        // save activity
        this.activity = activity
        // stop timer if exist
        timer?.let { internalStopTimer() }
        // set listener
        this.timeoutListener = timeoutListener
        // set interval
        this.intervalMin = intervalMin
        Log.d(TAG, "startTimer: intervalMin is set to $intervalMin.")
        // start timer
        startTimer(this.intervalMin * 60 * 1000L)
        // save action time for start
        updateActionTime()
        // start monitoring timeout
        isTimerActive = true
        Log.d(TAG, "startTimer: shouldCheckBackgroundTimeout is now $isTimerActive.")
    }

    /**
     * 常にintervalMillisの時間でタイマーをスタートする。
     * タイマーが切れたらタイムアウト処理を呼ぶ。
     * もしpublicメソッドを提供しなければならない場合、
     * intervalを受け取るが保存しないstartTemporaryTimerが必要。
     */
    private fun startTimer(intervalMillis: Long) {
        // start timer
        timer = Timer()
        timer!!.schedule(timerTask { timeout() }, intervalMillis)
        timerForActivity = Timer()
        var second = 0
        timerForActivity = timer(period = 1000) {
            timeoutListener.tick(second++)
        }
        Log.d(TAG, "startTimer: $intervalMillis milliseconds timer has started.")
    }

    /**
     * 稼働中のタイマーを止めて、バックグラウンドタイムアウトチェックを無効にする。
     */
    fun stopTimer() {
        timer?.let {
            internalStopTimer()
            isTimerActive = false
            Log.d(
                TAG,
                "stopTimer: shouldCheckBackgroundTimeout is now $isTimerActive."
            )
        }
    }

    /**
     * タイマーをストップする。
     */
    private fun internalStopTimer() {
        timer?.cancel()
        timer = null
        timerForActivity?.cancel()
        timerForActivity = null
        Log.d(TAG, "internalStopTimer: Timer has stopped.")
    }

    /**
     * 操作時刻を更新し、intervalMillisの時間でタイマーをリスタートする。
     */
    fun userDidAction() {
        if (isTimerActive) {
            Log.d(TAG, "userDidAction: Timer will restart.")
            updateActionTime()
            internalStopTimer()
            startTimer(intervalMin * 60 * 1000L)
        }
    }

    /**
     * 操作時刻を現在時刻で更新する。
     */
    private fun updateActionTime() {
        lastActionTime = LocalDateTime.now()
        Log.d(TAG, "updateActionTime: lastActionTime is now $lastActionTime.")
    }

    /**
     * アプリがバックグラウンドに移行した時に呼ばれる。
     * バックグラウンドタイムアウトチェックが：
     * - 有効な場合、最終操作時刻を保存する
     * - 無効な場合、何もしない
     */
    fun appDidEnterBackground() {
        Log.d(TAG, "appDidEnterBackground: ")
        internalStopTimer()
        if (isTimerActive) {
            saveLastActionTime()
        }
    }

    /**
     * アプリがフォアグラウンドに移行した時に呼ばれる。
     * バックグラウンドタイムアウトチェックが：
     * - 有効な場合、最終操作時刻と現在時刻でバックグラウンドタイムアウトをチェックする
     * - 無効な場合、何もしない
     */
    fun appWillEnterForeground() {
        Log.d(TAG, "appWillEnterForeground: ")
        if (isTimerActive) {
            val lastActionTime = loadLastActionTime()
            Log.d(TAG, "appWillEnterForeground: now is ${LocalDateTime.now()}")
            val durationMillis = Duration.between(lastActionTime, LocalDateTime.now()).toMillis()
            Log.d(TAG, "appWillEnterForeground: durationMillis is $durationMillis")

            if (durationMillis >= intervalMin * 60 * 1000L) {
                // 時間がintervalを超えている場合はタイムアウト
                timeout()
            } else {
                // 超えていない場合は残り時間でタイマーをスタート
                startTimer(intervalMin * 60 * 1000L - durationMillis)
            }
        }
    }

    /**
     * タイムアウト時処理。
     * タイムアウトリスナーにタイムアウトを通知する。
     */
    private fun timeout() {
        timerForActivity?.cancel()
        timerForActivity = null
        Log.d(TAG, "timeout: occurred.")
        isTimerActive = false
        timeoutListener.onTimeout()
    }

    /**
     * 最終操作時刻をSharedPreferencesに書き込む
     */
    private fun saveLastActionTime() {
        Log.d(TAG, "saveLastActionTime: $lastActionTime")
        val preferences = activity.getSharedPreferences("KEY_PREF", Context.MODE_PRIVATE)
        with(preferences.edit()) {
            putString("KEY_LAST_ACTION_TIME", lastActionTime.toString())
            apply()
        }
    }

    /**
     * 保存済みの最終操作時刻を取得する
     */
    private fun loadLastActionTime(): LocalDateTime {
        val preferences = activity.getSharedPreferences("KEY_PREF", Context.MODE_PRIVATE)
        val lastActionTime =
            preferences.getString("KEY_LAST_ACTION_TIME", LocalDateTime.now().toString())
        Log.d(TAG, "loadLastActionTime: $lastActionTime")
        return LocalDateTime.parse(lastActionTime)
    }
}