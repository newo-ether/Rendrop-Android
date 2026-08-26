package com.newoether.rendrop

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectInfoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesUuidProjectId() {
        val project = json.decodeFromString<ProjectInfo>(projectJson("\"4bfde949-04c1-455c-a5d6-518234e38bde\""))

        assertEquals("4bfde949-04c1-455c-a5d6-518234e38bde", project.id)
    }

    @Test
    fun decodesLegacyIntegerProjectIdAsString() {
        val project = json.decodeFromString<ProjectInfo>(projectJson("42"))

        assertEquals("42", project.id)
    }

    @Test
    fun ignoresAdditiveServerFields() {
        val project = json.decodeFromString<ProjectInfo>(
            projectJson("\"4bfde949-04c1-455c-a5d6-518234e38bde\"", "\"schemaVersion\": 2,")
        )

        assertEquals("C42", project.name)
    }

    private fun projectJson(id: String, extraField: String = ""): String = """
        {
            $extraField
            "id": $id,
            "name": "C42",
            "path": "C:/project/C42.blend",
            "outputPath": "//render/",
            "state": "Queued",
            "frameStart": 1,
            "frameEnd": 680,
            "frameStep": 1,
            "resolutionX": 1920,
            "resolutionY": 1080,
            "resolutionScale": 100,
            "renderEngine": "Cycles",
            "finishedFrame": 0,
            "totalFrame": 680
        }
    """.trimIndent()
}
