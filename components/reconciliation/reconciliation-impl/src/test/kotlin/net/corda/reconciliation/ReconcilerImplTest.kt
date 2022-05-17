package net.corda.reconciliation.impl

import net.corda.reconciliation.Reconciler
import net.corda.v5.base.util.uncheckedCast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ReconcilerImplTest {
    lateinit var reconciler: Reconciler

    @Test
    fun `reconciler name contains generic arguments`() {
        reconciler =
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
}