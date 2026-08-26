package com.newoether.rendrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectRepositoryTest {
    @Test
    fun successfulDeviceRefreshReplacesPreviousProjects() {
        val oldProject = project("old", "10.0.0.1")
        val newProject = project("new", "10.0.0.1")

        val result = mergeProjectRefresh(
            previousProjects = listOf(oldProject),
            deviceIps = listOf("10.0.0.1"),
            batch = ProjectRefreshBatch(
                projectsByDevice = mapOf("10.0.0.1" to listOf(newProject)),
                errors = emptyMap(),
            ),
        )

        assertEquals(listOf(newProject), result)
    }

    @Test
    fun failedDeviceRefreshKeepsLastSuccessfulProjects() {
        val previous = project("existing", "10.0.0.2")

        val result = mergeProjectRefresh(
            previousProjects = listOf(previous),
            deviceIps = listOf("10.0.0.2"),
            batch = ProjectRefreshBatch(
                projectsByDevice = emptyMap(),
                errors = mapOf("10.0.0.2" to RendropError.Network("offline")),
            ),
        )

        assertEquals(listOf(previous), result)
    }

    @Test
    fun removedDeviceProjectsAreDropped() {
        val retained = project("retained", "10.0.0.3")
        val removed = project("removed", "10.0.0.4")

        val result = mergeProjectRefresh(
            previousProjects = listOf(retained, removed),
            deviceIps = listOf("10.0.0.3"),
            batch = ProjectRefreshBatch(
                projectsByDevice = emptyMap(),
                errors = mapOf("10.0.0.3" to RendropError.Network("offline")),
            ),
        )

        assertEquals(listOf(retained), result)
        assertFalse(result.any { it.deviceIp == "10.0.0.4" })
    }

    private fun project(id: String, deviceIp: String) = ProjectInfo(
        id = id,
        name = id,
        path = "C:/$id.blend",
        outputPath = "//render/",
        state = "Queued",
        frameStart = 1,
        frameEnd = 10,
        frameStep = 1,
        resolutionX = 1920,
        resolutionY = 1080,
        resolutionScale = 100,
        renderEngine = "Cycles",
        finishedFrame = 0,
        totalFrame = 10,
        deviceIp = deviceIp,
    )
}
