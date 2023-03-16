package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.membership.lib.MemberInfoExtension
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.PublicKey

class SignerFactoryTest {
    @Test
    fun `factory create a signer`() {
        val cryptoOpsClient = mock<CryptoOpsClient>()
        val factory = SignerFactory(cryptoOpsClient)
        val publicKey = mock<PublicKey>()
        val memberContext = mock<MemberContext> {
            on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn "GroupId"
        }
        val mgm = mock<MemberInfo> {
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn MemberX500Name.parse("C=GB,L=London,O=mgm")
            on { sessionInitiationKey } doReturn publicKey
        }

        val signer = factory.createSigner(mgm)

        assertThat(signer).isInstanceOf(Signer::class.java)
    }
}
