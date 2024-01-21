package com.example.operationtimeoutsample

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import java.time.Duration
import java.time.LocalDateTime
import java.util.Timer
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity(), TimeoutListener {
    // TAG for logging
    private val tag = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            Log.d(tag, "onTouchEvent: touched screen.")
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

    override fun onTimeout() {
        TODO("Not yet implemented")
    }
}

interface TimeoutListener {
    fun onTimeout()
}

object TimeoutManager {
    private lateinit var activity: AppCompatActivity
    private var timer: Timer? = null
    private lateinit var timeoutListener: TimeoutListener
    private var intervalMin: Int = 0
    private var lastActionTime: LocalDateTime? = null
    private var shouldCheckBackgroundTimeout = false

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
        internalStopTimer()
        // set listener
        this.timeoutListener = timeoutListener
        // set interval
        this.intervalMin = intervalMin
        // start timer
        startTimer(this.intervalMin)
        // save action time for start
        updateActionTime()
        // start monitoring timeout
        shouldCheckBackgroundTimeout = true
    }

    /**
     * 常にintervalMinの時間でタイマーをスタートする。
     * タイマーが切れたらタイムアウト処理を呼ぶ。
     * もしpublicメソッドを提供しなければならない場合、
     * intervalを受け取るが保存しないstartTemporaryTimerが必要。
     */
    private fun startTimer(intervalMin: Int) {
        // calc Long interval
        val interval = intervalMin * 60 * 1000L
        // start timer
        timer = Timer()
        timer!!.schedule(timerTask { timeout() }, interval)
    }

    /**
     * 稼働中のタイマーを止めて、バックグラウンドタイムアウトチェックを無効にする。
     */
    fun stopTimer() {
        internalStopTimer()
        shouldCheckBackgroundTimeout = false
    }

    /**
     * タイマーをストップする。
     */
    private fun internalStopTimer() {
        timer?.cancel()
        timer = null
    }

    /**
     * 操作時刻を更新し、intervalMinの時間でタイマーをリスタートする。
     */
    fun userDidAction() {
        updateActionTime()
        internalStopTimer()
        startTimer(intervalMin)
    }

    /**
     * 操作時刻を現在時刻で更新する。
     */
    private fun updateActionTime() {
        lastActionTime = LocalDateTime.now()
    }

    /**
     * アプリがバックグラウンドに移行した時に呼ばれる。
     * バックグラウンドタイムアウトチェックが：
     * - 有効な場合、最終操作時刻を保存する
     * - 無効な場合、何もしない
     */
    fun appDidEnterBackground() {
        internalStopTimer()
        if (shouldCheckBackgroundTimeout) {
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
        if (shouldCheckBackgroundTimeout) {
            val lastActionTime = loadLastActionTime()
            val durationMin = Duration.between(lastActionTime, LocalDateTime.now()).toMinutes()

            if (durationMin >= intervalMin) {
                // 時間がintervalを超えている場合はタイムアウト
                timeout()
            } else {
                // 超えていない場合は残り時間でタイマーをスタート
                startTimer((intervalMin - durationMin).toInt())
            }
        }
    }

    /**
     * タイムアウト時処理。
     * タイムアウトリスナーにタイムアウトを通知する。
     */
    private fun timeout() {
        timeoutListener.onTimeout()
    }

    /**
     * 最終操作時刻をSharedPreferencesに書き込む
     */
    private fun saveLastActionTime() {
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
        val lastActionTime = preferences.getString("KEY_LAST_ACTION_TIME", LocalDateTime.now().toString())
        return LocalDateTime.parse(lastActionTime)
    }
}