package com.chaquo.python.utils

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.ViewModelProviders
import com.chaquo.python.Python
import com.chaquo.python.PyObject

abstract class PythonConsoleActivity : ConsoleActivity() {

    protected lateinit var task: Task

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        task = ViewModelProviders.of(this).get(getTaskClass())
        if (task.inputType != InputType.TYPE_NULL) {
            (findViewById<TextView>(resId("id", "etInput"))).inputType = task.inputType
        }
    }

    protected abstract fun getTaskClass(): Class<out Task>

    override fun onResume() {
        task.resumeStreams()
        super.onResume()  // Starts the task thread.
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) {
            task.pauseStreams()
        }
    }

    // =============================================================================================

    abstract class Task(application: Application) : ConsoleActivity.Task(application) {

        protected val py = Python.getInstance()
        private val console: PyObject = py.getModule("chaquopy.utils.console")
        private val sys: PyObject = py.getModule("sys")
        var inputType: Int
        private var stdin: PyObject? = null
        private var stdout: PyObject
        private var stderr: PyObject
        private var realStdin: PyObject? = null
        private var realStdout: PyObject
        private var realStderr: PyObject

        constructor(application: Application, inputType: Int) : this(application) {
            this.inputType = inputType
            if (inputType != InputType.TYPE_NULL) {
                realStdin = sys.get("stdin")
                stdin = console.callAttr("ConsoleInputStream", this)
            }

            realStdout = sys.get("stdout")
            realStderr = sys.get("stderr")
            stdout = redirectOutput(realStdout, "output")
            stderr = redirectOutput(realStderr, "outputError")
        }

        // We're not using method references, because that would prevent using this code with
        // old versions of Chaquopy.
        private fun redirectOutput(stream: PyObject, methodName: String): PyObject {
            return console.callAttr("ConsoleOutputStream", stream, this, methodName)
        }

        fun resumeStreams() {
            if (stdin != null) {
                sys.put("stdin", stdin)
            }
            sys.put("stdout", stdout)
            sys.put("stderr", stderr)
        }

        fun pauseStreams() {
            if (realStdin != null) {
                sys.put("stdin", realStdin)
            }
            sys.put("stdout", realStdout)
            sys.put("stderr", realStderr)
        }

        @Suppress("UNUSED_PARAMETER")  // Called from Python
        fun onInputState(blocked: Boolean) {
            if (blocked) {
                inputEnabled.postValue(true)
            }
        }

        override fun onInput(text: String?) {
            if (text != null) {
                // Messages which are empty (or only consist of newlines) will not be logged.
                Log.i("python.stdin", text.equals("\n") ? " " : text)
            }
            stdin?.callAttr("on_input", text)
        }

        override fun onCleared() {
            super.onCleared()
            if (stdin != null) {
                onInput(null)  // Signals EOF
            }
        }
    }
}