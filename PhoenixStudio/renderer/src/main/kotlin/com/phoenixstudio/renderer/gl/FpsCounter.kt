package com.phoenixstudio.renderer.gl

/**
 * Tracks frames-per-second over a rolling one-second window using only
 * primitive counters — no allocation per frame, which matters since this
 * is ticked from [PhoenixRenderer.onDrawFrame] on every frame on a
 * memory-constrained device.
 */
class FpsCounter {

    var currentFps: Int = 0
        private set

    private var frameCount = 0
    private var windowStartNanos = System.nanoTime()

    /** Call once per frame. Updates [currentFps] roughly once per second. */
    fun tick() {
        frameCount++
        val now = System.nanoTime()
        val elapsedNanos = now - windowStartNanos
        if (elapsedNanos >= ONE_SECOND_NANOS) {
            currentFps = (frameCount * ONE_SECOND_NANOS / elapsedNanos).toInt()
            frameCount = 0
            windowStartNanos = now
        }
    }

    companion object {
        private const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}
