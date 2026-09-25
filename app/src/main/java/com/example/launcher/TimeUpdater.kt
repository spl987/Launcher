package com.example.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUpdater {

    /**
     * 绑定系统时间到指定的 TextView
     * 调用后会自动在 onResume 注册广播，在 onPause 注销广播
     */
    fun bind(owner: LifecycleOwner, context: Context, tvTime: TextView, tvDate: TextView) {

        // 定义时间日期格式
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.US)

        // 内部更新时间的函数
        fun updateTime() {
            val now = Date()
            tvTime.text = timeFormat.format(now)
            tvDate.text = dateFormat.format(now)
        }

        // 立即更新一次，避免启动时短暂显示空白
        updateTime()

        // 定义广播接收者
        val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_TIME_TICK || intent?.action == Intent.ACTION_TIME_CHANGED) {
                    updateTime()
                }
            }
        }

        // 利用生命周期观察者，自动管理广播的注册与注销
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_TIME_CHANGED)
                }
                context.registerReceiver(timeReceiver, filter)
            }

            override fun onPause(owner: LifecycleOwner) {
                // 防止内存泄漏，销毁时一定要注销
                context.unregisterReceiver(timeReceiver)
            }
        })
    }
}