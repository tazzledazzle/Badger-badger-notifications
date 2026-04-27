package com.badger.notifications.channels

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TemplateRendererTest {
    @Test
    fun `replaces placeholders`() {
        val out =
            TemplateRenderer.render(
                "Hi {{name}}, order {{id}}",
                mapOf("name" to "Alex", "id" to "42"),
            )
        assertEquals("Hi Alex, order 42", out)
    }
}
