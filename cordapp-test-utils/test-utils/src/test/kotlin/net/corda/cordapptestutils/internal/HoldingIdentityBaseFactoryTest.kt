package net.corda.cordapptestutils.internal

import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class HoldingIdentityBaseFactoryTest {

    @Test
    fun `should construct a holding identity from a MemberX500`() {
        val member = MemberX500Name.parse("CN=IRunCordapps, OU=Application, O=R3, L=London, C=GB")
        val factory = HoldingIdentityBaseFactory()
        assertThat(factory.create(member), `is`(HoldingIdentityBase(member)))
    }
}