package net.corda.membership.certificate.client.impl

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicy.P2PParameters
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PropertyKeys.MGM_CLIENT_CERTIFICATE_SUBJECT
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

class MtlsMgmClientCertificateKeeperTest {
    private companion object {
        const val CURRENT_GROUP_POLICY_VERSION = 11L
    }
    private val mgmHoldingIdentity = HoldingIdentity(
        MemberX500Name.parse("C=GB, CN=Mgm, O=Mgm, L=LDN"),
        "group"
    )
    private val mgmMgmContext = mock<MGMContext> {
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn true
    }
    private val mgmMemberInfo = mock<MemberInfo> {
        on { mgmProvidedContext } doReturn mgmMgmContext
    }
    private val groupReader = mock<MembershipGroupReader> {
        on { lookup(mgmHoldingIdentity.x500Name) } doReturn mgmMemberInfo
    }
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(mgmHoldingIdentity) } doReturn groupReader
    }
    private val createdPropertyMap = mock<LayeredPropertyMap>()
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on { persistGroupPolicy(
            eq(mgmHoldingIdentity),
            eq(createdPropertyMap),
            any()
        ) } doReturn MembershipPersistenceResult.success()
    }
    private val savedGroupPolicy = mock<LayeredPropertyMap> {
        on { entries } doReturn mapOf("hello" to "world").entries
    }
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on { queryGroupPolicy(mgmHoldingIdentity) } doReturn MembershipQueryResult.Success(
            savedGroupPolicy to CURRENT_GROUP_POLICY_VERSION
        )
    }
    private val newlyCreatedMap = argumentCaptor<Map<String, String?>>()
    private val layeredPropertyMapFactory = mock<LayeredPropertyMapFactory> {
        on { createMap(newlyCreatedMap.capture()) } doReturn createdPropertyMap
    }
    private val certificate = mock<X509Certificate> {
        on { subjectX500Principal } doReturn X500Principal("C=GB, CN=Certificate, O=Subject, L=LDN")
    }
    private val certificateFactory = mock<CertificateFactory> {
        on { generateCertificates(any()) } doReturn listOf(certificate)
    }
    private val p2pParameters = mock<P2PParameters> {
        on { tlsType } doReturn TlsType.MUTUAL
    }
    private val groupProperty = mock<GroupPolicy> {
        on { p2pParameters } doReturn p2pParameters
    }

    private val keeper = MtlsMgmClientCertificateKeeper(
        membershipGroupReaderProvider,
        membershipPersistenceClient,
        membershipQueryClient,
        layeredPropertyMapFactory,
        certificateFactory
    )

    @Test
    fun `addMgmCertificateSubjectToGroupPolicy will add the certificate subject`() {
        keeper.addMgmCertificateSubjectToGroupPolicy(
            mgmHoldingIdentity,
            groupProperty,
            "certificate",
        )

        assertThat(newlyCreatedMap.firstValue)
            .containsEntry("hello", "world")
            .containsEntry(MGM_CLIENT_CERTIFICATE_SUBJECT, "CN=Certificate, O=Subject, L=LDN, C=GB")
    }

    @Test
    fun `addMgmCertificateSubjectToGroupPolicy will persist the new group policy`() {
        keeper.addMgmCertificateSubjectToGroupPolicy(
            mgmHoldingIdentity,
            groupProperty,
            "certificate",
        )

        verify(membershipPersistenceClient)
            .persistGroupPolicy(
                mgmHoldingIdentity,
                createdPropertyMap,
                CURRENT_GROUP_POLICY_VERSION + 1
            )
    }

    @Test
    fun `addMgmCertificateSubjectToGroupPolicy will throw an exception if persist fails`() {
        whenever(membershipPersistenceClient.persistGroupPolicy(any(), any(), any()))
            .doReturn(MembershipPersistenceResult.Failure("oops"))

        assertThrows<MembershipPersistenceResult.PersistenceRequestException> {
            keeper.addMgmCertificateSubjectToGroupPolicy(
                mgmHoldingIdentity,
                groupProperty,
                "certificate",
            )
        }
    }

    @Test
    fun `addMgmCertificateSubjectToGroupPolicy will throw an exception if query fails`() {
        whenever(membershipQueryClient
            .queryGroupPolicy(mgmHoldingIdentity)
        )
            .doReturn(MembershipQueryResult.Failure("oops"))

        assertThrows<MembershipQueryResult.QueryException> {
            keeper.addMgmCertificateSubjectToGroupPolicy(
                mgmHoldingIdentity,
                groupProperty,
                "certificate",
            )
        }
    }

    @Test
    fun `addMgmCertificateSubjectToGroupPolicy will throw an exception if the certificate is invalid`() {
        whenever(certificateFactory
            .generateCertificates(any())
        )
            .doReturn(emptyList())

        assertThrows<CordaRuntimeException> {
            keeper.addMgmCertificateSubjectToGroupPolicy(
                mgmHoldingIdentity,
                groupProperty,
                "certificate",
            )
        }
    }

    @Test
    fun `addMgmCertificateSubjectToGroupPolicy will read the correct certificate`() {
        val certificateInputStream = argumentCaptor<InputStream>()
        whenever(certificateFactory
            .generateCertificates(certificateInputStream.capture())
        )
            .doReturn(listOf(certificate))

        keeper.addMgmCertificateSubjectToGroupPolicy(
            mgmHoldingIdentity,
            groupProperty,
            "certificate",
        )

        val certificate = certificateInputStream.firstValue.reader().readText()
        assertThat(certificate).isEqualTo("certificate")
    }

    @Test
    fun `addMgmCertificateSubjectToGroupPolicy will do nothing for non MGM`() {
        val memberHoldingIdentity = HoldingIdentity(
            MemberX500Name.parse("C=GB, CN=Member, O=Member, L=LDN"),
            "group"
        )
        val memberMgmContext = mock<MGMContext> {
            on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn false
        }
        val memberMemberInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn memberMgmContext
        }
        whenever(groupReader.lookup(memberHoldingIdentity.x500Name)).doReturn(memberMemberInfo)
        whenever(membershipGroupReaderProvider.getGroupReader(memberHoldingIdentity)).doReturn(groupReader)

        keeper.addMgmCertificateSubjectToGroupPolicy(
            memberHoldingIdentity,
            groupProperty,
            "certificate",
        )

        verify(membershipPersistenceClient, never()).persistGroupPolicy(any(), any(), any())
    }

    @Test
    fun `addMgmCertificateSubjectToGroupPolicy will do nothing for non mutual TLS`() {
        whenever(p2pParameters.tlsType).doReturn(TlsType.ONE_WAY)

        keeper.addMgmCertificateSubjectToGroupPolicy(
            mgmHoldingIdentity,
            groupProperty,
            "certificate",
        )

        verify(membershipPersistenceClient, never()).persistGroupPolicy(any(), any(), any())
    }
}
