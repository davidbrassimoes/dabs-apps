package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonTest {
    @Test
    fun parsesAllValueKinds() {
        val v = Json.parse("""{ "a": [1, -2.5e1, true, false, null], "s": "x\"\\\n\u00e9", "o": {} }""") as Map<*, *>
        assertEquals(listOf(1.0, -25.0, true, false, null), v["a"])
        assertEquals("x\"\\\né", v["s"])
        assertEquals(emptyMap<String, Any?>(), v["o"])
        assertNull(Json.parse("null"))
    }

    @Test
    fun keepsKeyOrder() {
        val v = Json.parse("""{"z":1,"a":2,"m":3}""") as Map<*, *>
        assertEquals(listOf("z", "a", "m"), v.keys.toList())
    }

    @Test
    fun rejectsMalformedInput() {
        for (bad in listOf("{", "[1,]", "{\"a\" 1}", "tru", "1 2", "\"\\x\"", "{\"a\":1,}")) {
            assertThrows(bad, Json.ParseException::class.java) { Json.parse(bad) }
        }
    }
}
