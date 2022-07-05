package net.corda.reconciliation.impl

import net.corda.lifecycle.LifecycleCoordinator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class ReconcilerEventHandlerTest {

    lateinit var reconcilerEventHandler: ReconcilerEventHandler<*, *>

    @Test
    fun `reconciler event handler name contains generic arguments`() {
        reconcilerEventHandler =
            ReconcilerEventHandler(
                mock(),
                mock(),
                mock(),
                String::class.java,
                Int::class.java,
                1000L
            )
        Assertions.assertEquals(
            "${ReconcilerEventHandler::class.java.name}<${String::class.java.name}, ${Int::class.java.name}>",
            (reconcilerEventHandler).name
        )
    }

    @Test
    fun `on updating reconciliation interval sets new timer`(){
        reconcilerEventHandler =
            ReconcilerEventHandler(
                mock(),
                mock(),
                mock(),
                String::class.java,
                Int::class.java,
                1000L
            )

        val updateIntervalEvent = ReconcilerEventHandler.UpdateIntervalEvent(2000L)
        val coordinator = mock<LifecycleCoordinator>()

        reconcilerEventHandler.processEvent(updateIntervalEvent, coordinator)
        verify(coordinator).setTimer(eq(reconcilerEventHandler.name), eq(updateIntervalEvent.intervalMs), any())
        assertEquals(updateIntervalEvent.intervalMs, reconcilerEventHandler.reconciliationIntervalMs)
    }
}