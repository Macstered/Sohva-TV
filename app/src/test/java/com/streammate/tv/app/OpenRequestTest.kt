package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenRequestTest {
    @Test
    fun `a channel wins over an event, and nothing gives nothing`() {
        assertEquals(OpenRequest.Channel("c1"), OpenRequest.from("c1", "e1"))
        assertEquals(OpenRequest.Event("e1"), OpenRequest.from(null, "e1"))
        assertEquals(OpenRequest.Event("e1"), OpenRequest.from("", "e1"))
        assertNull(OpenRequest.from(null, null))
        assertNull(OpenRequest.from("", " ".trim()))
    }
}
