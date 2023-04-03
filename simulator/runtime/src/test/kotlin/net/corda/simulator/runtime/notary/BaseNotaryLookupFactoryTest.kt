package net.corda.simulator.runtime.notary

import net.corda.simulator.runtime.messaging.SimFiberBase
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.security.PublicKey

class BaseNotaryLookupFactoryTest {

    @Test
    fun `should create notary lookup which return the notary`(){
        //Given the simFiber and a notaryInfo
        val fiber = SimFiberBase()
        val notaryX500 = MemberX500Name.parse("CN=SimulatorNotaryService, OU=Simulator, O=R3, L=London, C=GB")
        val notaryKey = mock<PublicKey>()
        val notaryInfo = BaseNotaryInfo(notaryX500, "", emptySet(), notaryKey)

        // When we create a notaryLookup
        val notaryLookup = BaseNotaryLookupFactory().createNotaryLookup(fiber, notaryInfo)

        //Then it should be able to fetch the notary
        assertThat(notaryLookup.lookup(notaryX500), `is`(notaryInfo))
        assertThat(notaryLookup.notaryServices, `is`(listOf(notaryInfo)))
    }
}