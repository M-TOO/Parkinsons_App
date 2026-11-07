package com.example.pdscreen

/**
 * Records press/release times for ONE typing excerpt. Call from your own on-screen keyboard
 * (MotionEvent.eventTime). Standard Android keyboards don't expose key-up times.
 * Collect 5+ excerpts (20+ keystrokes each), then send them all to the backend.
 */
class KeyEventRecorder {
    private val downAt = HashMap<Int, Long>()
    private val events = ArrayList<List<Long>>()  // [press, release]

    fun onPress(keyId: Int, uptimeMs: Long) { downAt[keyId] = uptimeMs }
    fun onRelease(keyId: Int, uptimeMs: Long) { downAt.remove(keyId)?.let { events.add(listOf(it, uptimeMs)) } }
    fun finishExcerpt(): List<List<Long>> = events.sortedBy { it[0] }.also { events.clear(); downAt.clear() }
}
