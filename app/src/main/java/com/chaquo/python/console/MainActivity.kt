package com.chaquo.python.console

import android.app.Activity
import com.chaquo.python.utils.PythonConsoleActivity

class MainActivity : PythonConsoleActivity() {

    override fun onBackPressed() {
        finish()
    }

    override fun getTaskClass(): Class<out PythonConsoleActivity.Task?> {
        return Task::class.java
    }

    class Task(app: Activity) : PythonConsoleActivity.Task(app) {
        override fun run() {
            py.getModule("main").callAttr("main")
        }
    }
}