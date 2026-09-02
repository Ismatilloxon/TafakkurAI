package com.example.tafakkurai.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityLogTest {

    @Test
    fun payloadKeepsPropertiesAndProtectsEnvelopeFields() {
        val raw = activityPayload(
            type = "screen_viewed",
            at = 42L,
            properties = buildJsonObject {
                put("route", "today")
                put("type", "caller-cannot-overwrite")
                put("at", -1)
            },
        )
        val payload = syncJson.parseToJsonElement(raw) as JsonObject

        assertEquals("screen_viewed", payload.getValue("type").jsonPrimitive.content)
        assertEquals(42L, payload.getValue("at").jsonPrimitive.content.toLong())
        assertEquals("today", payload.getValue("route").jsonPrimitive.content)
    }

    @Test
    fun generatedIdsAreUuidV4() {
        repeat(100) {
            assertTrue(
                activityUuidV4().matches(
                    Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"),
                ),
            )
        }
    }
}
