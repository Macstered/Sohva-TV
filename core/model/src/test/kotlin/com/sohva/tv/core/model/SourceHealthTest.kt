package com.sohva.tv.core.model

import com.sohva.tv.core.model.source.EpgOffsetLabel
import com.sohva.tv.core.model.source.RefreshState
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceHealthTest {
    @Test
    fun offsetLabelsUseHoursAndMinutesAndATrueMinus() {
        assertEquals("0 min", EpgOffsetLabel.of(0))
        assertEquals("+30 min", EpgOffsetLabel.of(30))
        assertEquals("−30 min", EpgOffsetLabel.of(-30))
        assertEquals("+1 h", EpgOffsetLabel.of(60))
        assertEquals("+1 h 30 min", EpgOffsetLabel.of(90))
        assertEquals("−12 h", EpgOffsetLabel.of(-720))
    }

    @Test
    fun unknownStoredStatesReadAsIdle() {
        assertEquals(RefreshState.RUNNING, RefreshState.fromStored("running"))
        assertEquals(RefreshState.IDLE, RefreshState.fromStored("paused-by-a-newer-build"))
        assertEquals(RefreshState.IDLE, RefreshState.fromStored(null))
    }
}
