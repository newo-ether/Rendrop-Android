package com.newoether.rendrop

internal object VideoWorkInput {
    const val PROJECT_NAME = "projectName"
    const val DEVICE_IP = "deviceIp"
    const val PROJECT_ID = "projectId"
    const val FRAME_START = "frameStart"
    const val FRAME_COUNT = "frameCount"
    const val FRAME_STEP = "frameStep"
    const val LEGACY_FRAME_NUMBERS = "frameNumbers"
    const val QUALITY = "quality"
    const val FPS = "fps"
}

internal fun buildFrameNumbers(start: Int, count: Int, step: Int): IntArray? {
    if (count <= 0 || step <= 0) return null

    val lastFrame = start.toLong() + (count - 1L) * step.toLong()
    if (lastFrame !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null

    return IntArray(count) { index ->
        (start.toLong() + index.toLong() * step.toLong()).toInt()
    }
}
