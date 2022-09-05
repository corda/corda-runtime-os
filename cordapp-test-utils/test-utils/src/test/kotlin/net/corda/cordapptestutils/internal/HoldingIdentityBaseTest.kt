package net.corda.cordapptestutils.internal

import net.corda.cordapptestutils.HoldingIdentity
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.isA
import org.junit.jupiter.api.Test

class HoldingIdentityBaseTest {

    private val holdingId = HoldingIdentity.create("IRunCordapps")

    @Test
    fun `should be a HoldingIdentity`() {
        assertThat(holdingId, isA(HoldingIdentity::class.java))
    }
}