package com.example.operationtimeoutsample

/**
 * タイムアウトリスナーインターフェース
 */
interface TimeoutListener {
    fun onTimeout()

    fun tick(second: Int)
}