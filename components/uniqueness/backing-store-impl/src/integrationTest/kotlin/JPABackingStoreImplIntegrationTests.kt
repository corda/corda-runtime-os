package net.corda.uniqueness.backingstore.impl

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplIntegrationTests {

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `placeholder`() {
            assert(true)
        }
    }
}