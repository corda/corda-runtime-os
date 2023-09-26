package net.corda.reconciliation.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.VersionedRecord
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    @Test
    fun `on forceInitialReconciliation only the first reconciliation is force reconciled`() {
        val dbRecord = object : VersionedRecord<String, Int> {
            override val version: Int
                get() = 1
            override val isDeleted: Boolean
                get() = false
            override val key: String
                get() = "key1"
            override val value: Int
                get() = 1
        }

        val kafkaRecord = object : VersionedRecord<String, Int> {
            override val version: Int
                get() = 1
            override val isDeleted: Boolean
                get() = false
            override val key: String
                get() = "key1"
            override val value: Int
                get() = 1
        }

        val dbReader = mock<ReconcilerReader<String, Int>>().also {
            whenever(it.getAllVersionedRecords()).doAnswer {
                listOf<VersionedRecord<String, Int>>(dbRecord).stream()
            }
        }

        val kafkaReader = mock<ReconcilerReader<String, Int>>().also {
            whenever(it.getAllVersionedRecords()).doAnswer {
                listOf<VersionedRecord<String, Int>>(kafkaRecord).stream()
            }
        }

        reconcilerEventHandler =
            ReconcilerEventHandler(
                dbReader,
                kafkaReader,
                writer = mock(),
                keyClass = String::class.java,
                valueClass = Int::class.java,
                10L,
                forceInitialReconciliation = true
            )

        val reconciledOnFirstReconciliation = reconcilerEventHandler.reconcile()
        val reconciledOnSecondReconciliation = reconcilerEventHandler.reconcile()
        assertEquals(1, reconciledOnFirstReconciliation)
        assertEquals(0, reconciledOnSecondReconciliation)
    }
}