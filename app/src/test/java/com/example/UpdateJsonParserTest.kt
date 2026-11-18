package com.example

import com.example.updates.UpdateJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateJsonParserTest {

    private val metadata = """
        {
          "versionCode": 26,
          "versionName": "8.18",
          "tag": "v8.18.0",
          "apk": "PrevisorDatos_8.18.0.apk",
          "sha256": "0A3D2D6F78293512BE47D8F9E24AFBCA197093782E6470C3D4BC5D49C25F4AC5",
          "size": 1627532
        }
    """.trimIndent()

    @Test
    fun `parses the metadata emitted by CI`() {
        val info = UpdateJsonParser.parse(metadata)!!

        assertEquals(26, info.versionCode)
        assertEquals("8.18", info.versionName)
        assertEquals("v8.18.0", info.tag)
        assertEquals("PrevisorDatos_8.18.0.apk", info.apkName)
        assertEquals(1_627_532L, info.sizeBytes)
        assertEquals("0a3d2d6f78293512be47d8f9e24afbca197093782e6470c3d4bc5d49c25f4ac5", info.sha256)
        assertEquals(
            "https://github.com/renehdezalvarez/Datos_android/releases/download/v8.18.0/PrevisorDatos_8.18.0.apk",
            info.apkUrl
        )
    }

    @Test
    fun `rejects malformed or incomplete metadata`() {
        assertNull(UpdateJsonParser.parse("not json"))
        assertNull(UpdateJsonParser.parse("""{"versionCode": 26}"""))
        assertNull(UpdateJsonParser.parse(metadata.replace("\"sha256\":", "\"hash\":")))
    }
}
