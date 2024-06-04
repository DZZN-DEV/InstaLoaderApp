package com.chaquo.python.utils

import android.app.Application
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.chaquo.python.utils.ConsoleActivity.Task
import java.util.*

abstract class ConsoleActivity : AppCompatActivity(), ViewTreeObserver.OnGlobalLayoutListener,
    ViewTreeObserver.OnScrollChangedListener {

    private val MAX_SCROLLBACK_LEN = 100000

    private lateinit var etInput: EditText
    private lateinit var svOutput: ScrollView
    private lateinit var tvOutput: TextView
    private var outputWidth = -1
    private var outputHeight = -1

    private var scrollRequest: Scroll? = null

    enum class Scroll {
        TOP, BOTTOM
    }

    class ConsoleModel : AndroidViewModel {

        constructor(application: Application) : super(application)

        var pendingNewline = false
        var scrollChar = 0
        var scrollAdjust = 0
    }

    private lateinit var consoleModel: ConsoleModel

    protected lateinit var task: Task

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consoleModel = ViewModelProviders.of(this).get(ConsoleModel::class.java)
        task = ViewModelProviders.of(this).get(getTaskClass())
        setContentView(resId("layout", "activity_console"))
        createInput()
        createOutput()
    }

    protected abstract fun getTaskClass(): Class<out Task>

    private fun createInput() {
        etInput = findViewById(resId("id", "etInput"))

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) {
                e?.getSpans(0, e.length, CharacterStyle::class.java)?.forEach {
                    e.removeSpan(it)
                }
            }
        })

        etInput.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.action == KeyEvent.ACTION_UP)
            ) {
                val text = etInput.text.toString() + "\n"
                etInput.setText("")
                output(span(text, StyleSpan(Typeface.BOLD)))
                scrollTo(Scroll.BOTTOM)
                task.onInput(text)
            }
            true
        })

        task.inputEnabled.observe(this, Observer { enabled ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (enabled == true) {
                etInput.visibility = View.VISIBLE
                etInput.isEnabled = true
                etInput.requestFocus()
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
            } else {
                etInput.isEnabled = false
                imm.hideSoftInputFromWindow(tvOutput.windowToken, 0)
            }
        })
    }

    private fun createOutput() {
        svOutput = findViewById(resId("id", "svOutput"))
        svOutput.viewTreeObserver.addOnGlobalLayoutListener(this)

        tvOutput = findViewById(resId("id", "tvOutput"))
        if (Build.VERSION.SDK_INT >= 23) {
            tvOutput.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        if (task.state != Thread.State.NEW) {
            super.onRestoreInstanceState(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()
        if (task.state == Thread.State.NEW) {
            task.start()
        }
    }

    override fun onPause() {
        super.onPause()
        saveScroll()
    }

    override fun onGlobalLayout() {
        if (outputWidth != svOutput.width || outputHeight != svOutput.height) {
            if (outputWidth == -1) {
                svOutput.viewTreeObserver.addOnScrollChangedListener(this)
            }
            outputWidth = svOutput.width
            outputHeight = svOutput.height
            restoreScroll()
        } else if (scrollRequest != null) {
            var y = -1
            when (scrollRequest) {
                Scroll.TOP -> y = 0
                Scroll.BOTTOM -> y = tvOutput.height
            }
            svOutput.scrollTo(0, y)
            scrollRequest = null
        }
    }

    override fun onScrollChanged() {
        saveScroll()
    }

    private fun saveScroll() {
        if (isScrolledToBottom()) {
            consoleModel.scrollChar = tvOutput.text.length
            consoleModel.scrollAdjust = 0
        } else {
            val scrollY = svOutput.scrollY
            val layout = tvOutput.layout
            if (layout != null) {
                val line = layout.getLineForVertical(scrollY)
                consoleModel.scrollChar = layout.getLineStart(line)
                consoleModel.scrollAdjust = scrollY - layout.getLineTop(line)
            }
        }
    }

    private fun restoreScroll() {
        removeCursor()
        val layout = tvOutput.layout
        if (layout != null) {
            val line = layout.getLineForOffset(consoleModel.scrollChar)
            svOutput.scrollTo(0, layout.getLineTop(line) + consoleModel.scrollAdjust)
        }
        saveScroll()
        task.output.removeObservers(this)
        task.output.observe(this, Observer { text ->
            output(text)
        })
    }

    private fun isScrolledToBottom(): Boolean {
        val visibleHeight = svOutput.height - svOutput.paddingTop - svOutput.paddingBottom
        val maxScroll = Math.max(0, tvOutput.height - visibleHeight)
        return svOutput.scrollY >= maxScroll
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(resId("menu", "top_bottom"), menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            resId("id", "menu_top") -> {
                scrollTo(Scroll.TOP)
                true
            }
            resId("id", "menu_bottom") -> {
                scrollTo(Scroll.BOTTOM)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun span(text: CharSequence, vararg spans: Any): Spannable {
        val spanText = SpannableStringBuilder(text)
        spans.forEach {
            spanText.setSpan(it, 0, text.length, 0)
        }
        return spanText
    }

    private fun output(text: CharSequence) {
        removeCursor()
        if (consoleModel.pendingNewline) {
            tvOutput.append("\n")
            consoleModel.pendingNewline = false
        }
        if (text[text.length - 1] == '\n') {
            tvOutput.append(text.subSequence(0, text.length - 1))
            consoleModel.pendingNewline = true
        } else {
            tvOutput.append(text)
        }
    
        val scrollback = tvOutput.text as Editable
        if (scrollback.length > MAX_SCROLLBACK_LEN) {
            scrollback.delete(0, MAX_SCROLLBACK_LEN / 10)
        }
    
        if (isScrolledToBottom()) {
            scrollTo(Scroll.BOTTOM)
        }
    }
    
    private fun scrollTo(request: Scroll) {
        if (scrollRequest != Scroll.TOP) {
            scrollRequest = request
            svOutput.requestLayout()
        }
    }
    
    private fun removeCursor() {
        val text = tvOutput.text as Spannable
        val selStart = Selection.getSelectionStart(text)
        val selEnd = Selection.getSelectionEnd(text)
    
        if (!(text is Editable)) {
            tvOutput.text = text
            tvOutput.setText(text, TextView.BufferType.EDITABLE)
    
            if (selStart >= 0) {
                Selection.setSelection(text, selStart, selEnd)
            }
        }
    
        if (selStart >= 0 && selStart == selEnd) {
            Selection.removeSelection(text)
        }
    }
    
    fun resId(type: String, name: String): Int {
        return Utils.resId(this, type, name)
    }

    abstract class Task(app: Application) : AndroidViewModel(app) {
    
        private var state: Thread.State = Thread.State.NEW
    
        fun start() {
            Thread {
                try {
                    run()
                    output(spanColor("[Finished]", resId("color", "console_meta")))
                } finally {
                    inputEnabled.postValue(false)
                    state = Thread.State.TERMINATED
                }
            }.start()
            state = Thread.State.RUNNABLE
        }
    
        fun getState(): Thread.State = state
    
        val inputEnabled = MutableLiveData<Boolean>().apply { value = false }
        val output = BufferedLiveEvent<CharSequence>()
    
        abstract fun run()
    
        open fun onInput(text: String) {}
    
        fun output(text: CharSequence) {
            if (text.isEmpty()) return
            output.postValue(text)
        }
    
        fun outputError(text: CharSequence) {
            output(spanColor(text, resId("color", "console_error")))
        }
    
        fun spanColor(text: CharSequence, colorId: Int): Spannable {
            val color = ContextCompat.getColor(application, colorId)
            return span(text, ForegroundColorSpan(color))
        }
    
        fun resId(type: String, name: String): Int = Utils.resId(application, type, name)
    }
}