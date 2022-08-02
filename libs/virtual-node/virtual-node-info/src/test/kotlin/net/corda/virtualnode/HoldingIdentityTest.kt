package net.corda.virtualnode

import net.corda.test.util.identity.createTestHoldingIdentity
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class HoldingIdentityTest {

    @Test
    fun getId() {
        val holdingIdentityA = createTestHoldingIdentity("C=GB,L=London,O=PartyA", "b8baa6fc-4c77-11ec-8b1e-bb51725ace52")
        val holdingIdentityB = createTestHoldingIdentity("C=GB,L=London,O=PartyB", "b8baa6fc-4c77-11ec-8b1e-bb51725ace52")
        val holdingIdentityC = createTestHoldingIdentity("C=GB,L=London,O=PartyB", "b8baa6fc-4c77-11ec-8b1e-bb51725ace53")

        assertNotNull(holdingIdentityA.shortHash)
        assertNotNull(holdingIdentityB.shortHash)
        assertNotNull(holdingIdentityC.shortHash)

        assertNotEquals(holdingIdentityA.shortHash, holdingIdentityB.shortHash)
        assertNotEquals(holdingIdentityB.shortHash, holdingIdentityC.shortHash)
        assertNotEquals(holdingIdentityC.shortHash, holdingIdentityA.shortHash)
    }
}
