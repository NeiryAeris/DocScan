package com.example.docscan.logic.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Locale

object DebugLog {
    private const val DEFAULT_TAG = "DocScan"
    private const val MAX_LINES = 400

    private val dateFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _lines = ArrayDeque<String>(MAX_LINES)
    private val _stream = MutableStateFlow<List<String>>(emptyList())
    val stream: StateFlow<List<String>> get() = _stream

    private fun push(line: String) {
        if (_lines.size == MAX_LINES) _lines.removeFirst()
        _lines.addLast(line)
        _stream.value = _lines.toList()
    }

    private fun stamp(): String = dateFmt.format(System.currentTimeMillis())

    fun d(msg: String, tag: String = DEFAULT_TAG) {
        Log.d(tag, msg)
        push("${stamp()} D/$tag: $msg")
    }
    fun i(msg: String, tag: String = DEFAULT_TAG) {
        Log.i(tag, msg)
        push("${stamp()} I/$tag: $msg")
    }
    fun w(msg: String, tag: String = DEFAULT_TAG, tr: Throwable? = null) {
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        push("${stamp()} W/$tag: $msg" + (tr?.let { "  (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
    }
    fun e(msg: String, tag: String = DEFAULT_TAG, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        push("${stamp()} E/$tag: $msg" + (tr?.let { "  (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
    }
}
