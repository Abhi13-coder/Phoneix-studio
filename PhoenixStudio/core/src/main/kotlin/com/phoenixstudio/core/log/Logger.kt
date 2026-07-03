package com.phoenixstudio.core.log

import android.util.Log

/**
 * Central logging facade for Phoenix Studio.
 *
 * Every module logs through this object instead of calling [android.util.Log]
 * directly, for two reasons:
 *  1. The editor's console panel (bottom pane of the main screen) needs to
 *     mirror engine output in-app, on-device — [addSink] lets the UI module
 *     subscribe without core depending on any UI type.
 *  2. It gives a single place to add file-backed logging (Logs/ directory)
 *     once the filesystem module lands, without touching call sites.
 */
object Logger {

    /** A single captured log line, exposed to sinks such as the in-app console. */
    data class Entry(
        val level: Level,
        val tag: String,
        val message: String,
        val timestampMillis: Long
    )

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val sinks = mutableListOf<(Entry) -> Unit>()

    /**
     * Registers a callback invoked for every subsequent log entry.
     * Returns a handle that can be passed to [removeSink] to unsubscribe,
     * which the editor UI does in onDestroy to avoid leaking the Activity.
     */
    fun addSink(sink: (Entry) -> Unit): (Entry) -> Unit {
        sinks.add(sink)
        return sink
    }

    fun removeSink(sink: (Entry) -> Unit) {
        sinks.remove(sink)
    }

    fun d(tag: String, message: String) = emit(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = emit(Level.INFO, tag, message)
    fun w(tag: String, message: String) = emit(Level.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message :: ${throwable.stackTraceToString()}" else message
        emit(Level.ERROR, tag, full)
    }

    private fun emit(level: Level, tag: String, message: String) {
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.WARN -> Log.w(tag, message)
            Level.ERROR -> Log.e(tag, message)
        }
        val entry = Entry(level, tag, message, System.currentTimeMillis())
        for (sink in sinks) {
            sink(entry)
        }
    }
}
