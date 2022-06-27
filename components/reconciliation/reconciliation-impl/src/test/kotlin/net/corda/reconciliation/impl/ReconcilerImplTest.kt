package net.corda.reconciliation.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.reconciliation.Reconciler
import net.corda.v5.base.util.uncheckedCast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ReconcilerImplTest {

    @Test
    fun `reconciler name contains generic arguments`() {
        val reconciler =
            ReconcilerImpl(
                mock(),
                mock(),
                mock(),
                String::class.java,
                Int::class.java,
                mock(),
                1000L
            )
        assertEquals(
            "${ReconcilerImpl::class.java.name}<${String::class.java.name}, ${Int::class.java.name}>",
            uncheckedCast<Reconciler, ReconcilerImpl<*, *>>(reconciler).name
        )
    }

    @Test
    fun `on updating reconciliation interval sets new timer`(){
        val reconciler =
            ReconcilerImpl(
                mock(),
                mock(),
                mock(),
                String::class.java,
                Int::class.java,
                mock(),
                1000L
            )

        val updateIntervalEvent = ReconcilerImpl.UpdateIntervalEvent(2000L)
        val coordinator = mock<LifecycleCoordinator>()

        reconciler.processEvent(updateIntervalEvent, coordinator)
        verify(coordinator).setTimer(eq(reconciler.name), eq(updateIntervalEvent.intervalMs), any())
        assertEquals(updateIntervalEvent.intervalMs, reconciler.reconciliationIntervalMs)
    }
}