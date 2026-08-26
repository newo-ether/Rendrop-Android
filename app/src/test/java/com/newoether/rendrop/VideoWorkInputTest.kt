package com.newoether.rendrop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoWorkInputTest {
    @Test
    fun buildsFrameRange() {
        assertArrayEquals(intArrayOf(10, 13, 16, 19), buildFrameNumbers(10, 4, 3))
    }

    @Test
    fun rejectsInvalidCountAndStep() {
        assertNull(buildFrameNumbers(1, 0, 1))
        assertNull(buildFrameNumbers(1, -1, 1))
        assertNull(buildFrameNumbers(1, 2, 0))
        assertNull(buildFrameNumbers(1, 2, -1))
    }

    @Test
    fun rejectsIntegerOverflow() {
        assertNull(buildFrameNumbers(Int.MAX_VALUE - 1, 3, 1))
    }
}
