package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class SignerFactoryTest {
    private val memberContext = mock<MemberContext> {
        on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn "GroupId"
    }
    private val mgm = mock<MemberInfo> {
        on { memberProvidedContext } doReturn memberContext
        on { name } doReturn MemberX500Name.parse("C=GB,L=London,O=mgm")
    }
    private val publicKey = mock<PublicKey>()
    private val hostedIdentityInfo = mock<IdentityInfo> {
        on { preferredSessionKey } doReturn publicKey
    }
    private val membershipHostingMap = mock<LocallyHostedIdentitiesService> {
        on { pollForIdentityInfo(mgm.holdingIdentity) } doReturn hostedIdentityInfo
    }
    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private val factory = SignerFactory(cryptoOpsClient, membershipHostingMap)

    @Test
    fun `factory create a signer`() {
        val signer = factory.createSigner(mgm)

        assertThat(signer).isInstanceOf(Signer::class.java)
    }

    @Test
    fun `factory throws an exception if identity can not be found`() {
        whenever(membershipHostingMap.pollForIdentityInfo(any())).doReturn(null)

        assertThrows<IllegalStateException> {
            factory.createSigner(mgm)
        }
    }
}
