package com.example.launcher

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 绑定布局文件
        setContentView(R.layout.activity_main)


        TimeUpdater.bind(
            owner = this,
            context = this,
            tvTime = findViewById(R.id.tv_time),
            tvDate = findViewById(R.id.tv_date)
        )

        // ================= 统一添加焦点缩放动画 =================
        val focusViews = listOf(
            R.id.iv_netflix, R.id.iv_youtube, R.id.iv_google_play, R.id.iv_chrome,
            R.id.btn_keystone, R.id.btn_miracast, R.id.btn_signal, R.id.btn_my_apps, R.id.btn_settings
        )

        // 遍历列表，给每一个控件加上动画
        focusViews.forEach { id ->
            findViewById<View>(id).addFocusScaleAnimation()
        }
        // ========================================================
    }
}

fun View.addFocusScaleAnimation() {
    this.setOnFocusChangeListener { v, hasFocus ->
        if (hasFocus) {
            v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(200).start()
        } else {
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }
}