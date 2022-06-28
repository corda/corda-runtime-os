package net.corda.membership.certificate.client.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.GroupPolicy
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.cert.CertificateFactory

class HostedIdentityEntryFactoryTest {
    private companion object {
        val validHoldingId = HoldingIdentity(
            x500Name = "x500node",
            groupId = "group-1"
        )
        val publicKeyBytes = "123".toByteArray()
        const val VALID_NODE = "validNode"
        const val INVALID_NODE = "invalidNode"
        const val PUBLIC_KEY_PEM = "publicKeyPem"
        const val VALID_CERTIFICATE_ALIAS = "alias"
    }
    private val nodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn validHoldingId
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getById(VALID_NODE) } doReturn nodeInfo
        on { getById(INVALID_NODE) } doReturn null
    }
    private val sessionKey = mock<CryptoSigningKey> {
        on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytes)
    }
    private val certificatePem =
        HostedIdentityEntryFactoryTest::class.java.getResource("/certificates/$VALID_NODE.pem")!!.readText()
            .replace("\n", System.lineSeparator())
    private val rootPem = HostedIdentityEntryFactoryTest::class.java.getResource("/certificates/root.pem")!!.readText()
    private val certificatePublicKey = certificatePem.let {
        CertificateFactory.getInstance("X.509").generateCertificate(it.byteInputStream()).publicKey
    }
    private val filter = argumentCaptor<Map<String, String>>()
    private val ids = argumentCaptor<List<String>>()
    private val cryptoOpsClient = mock<CryptoOpsClient> {
        on {
            lookup(
                eq(VALID_NODE),
                eq(0),
                eq(1),
                eq(CryptoKeyOrderBy.NONE),
                filter.capture()
            )
        } doReturn listOf(sessionKey)
        on {
            lookup(
                eq(VALID_NODE),
                ids.capture()
            )
        } doReturn listOf(sessionKey)

        on {
            filterMyKeys(eq(VALID_NODE), eq(listOf(certificatePublicKey)))
        }.doReturn(listOf(certificatePublicKey))
        on {
            filterMyKeys(eq(P2P), eq(listOf(certificatePublicKey)))
        }.doReturn(listOf(certificatePublicKey))
    }
    private val sessionPublicKey = mock<PublicKey>()
    private val keyEncodingService = mock<KeyEncodingService> {
        on { decodePublicKey(publicKeyBytes) } doReturn sessionPublicKey
        on { encodeAsString(sessionPublicKey) } doReturn PUBLIC_KEY_PEM
    }
    private val groupPolicy = mock<GroupPolicy> {
        on { get("p2pParameters") } doReturn mapOf("tlsTrustRoots" to listOf(rootPem))
    }
    private val groupPolicyProvider = mock<GroupPolicyProvider> {
        on { getGroupPolicy(validHoldingId) } doReturn groupPolicy
    }

    private val factory = HostedIdentityEntryFactory(
        virtualNodeInfoReadService,
        cryptoOpsClient,
        keyEncodingService,
        groupPolicyProvider,
    ) { _, alias ->
        if (alias == VALID_CERTIFICATE_ALIAS) {
            certificatePem
        } else {
            null
        }
    }

    @Test
    fun `createIdentityRecord create the correct record`() {
        val record = factory.createIdentityRecord(
            holdingIdentityId = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = null,
            sessionKeyId = null
        )

        assertSoftly { softly ->
            softly.assertThat(record.topic).isEqualTo(P2P_HOSTED_IDENTITIES_TOPIC)
            softly.assertThat(record.key).isEqualTo(validHoldingId.id)
            softly.assertThat(record.value).isEqualTo(
                HostedIdentityEntry(
                    validHoldingId.toAvro(),
                    VALID_NODE,
                    VALID_NODE,
                    listOf(certificatePem),
                    PUBLIC_KEY_PEM
                )
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception for invalid node`() {
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityId = INVALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = VALID_NODE,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will look for ID if provided`() {
        factory.createIdentityRecord(
            holdingIdentityId = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = VALID_NODE,
            sessionKeyId = "id1"
        )

        assertThat(ids.firstValue)
            .hasSize(1)
            .contains("id1")
    }

    @Test
    fun `createIdentityRecord will filter by category if no id is provided`() {
        factory.createIdentityRecord(
            holdingIdentityId = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = VALID_NODE,
            sessionKeyId = null
        )

        assertThat(filter.firstValue)
            .hasSize(1)
            .containsEntry(CATEGORY_FILTER, SESSION_INIT)
    }

    @Test
    fun `createIdentityRecord will throw an exception if key can not be found`() {
        whenever(cryptoOpsClient.lookup(any(), any(), any(), any(), any())).doReturn(emptyList())
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityId = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = VALID_NODE,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if certificates can not be found`() {
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityId = VALID_NODE,
                certificateChainAlias = "NOP",
                tlsTenantId = VALID_NODE,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord with another TLS tenant will call the certificates from that tenant`() {
        var tenantId: String? = null
        val factory = HostedIdentityEntryFactory(
            virtualNodeInfoReadService,
            cryptoOpsClient,
            keyEncodingService,
            groupPolicyProvider,
        ) { tenant, _ ->
            tenantId = tenant
            certificatePem
        }

        factory.createIdentityRecord(
            holdingIdentityId = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = P2P,
            sessionKeyId = null
        )

        assertThat(tenantId).isEqualTo("p2p")
    }

    @Test
    fun `createIdentityRecord with another TLS tenant will return that ID`() {
        val record = factory.createIdentityRecord(
            holdingIdentityId = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = P2P,
            sessionKeyId = null
        )

        assertThat(record.value?.tlsTenantId).isEqualTo("p2p")
    }

    @Test
    fun `createIdentityRecord will throw an exception if the certificates are empty`() {
        val factory = HostedIdentityEntryFactory(
            virtualNodeInfoReadService,
            cryptoOpsClient,
            keyEncodingService,
            groupPolicyProvider,
        ) { _, _ ->
            "\n"
        }

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityId = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyId = null
            )
        }
    }
    @Test
    fun `createIdentityRecord will throw an exception if the certificate public key is unknown`() {
        whenever(cryptoOpsClient.filterMyKeys(any(), any())).doReturn(emptyList())

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityId = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group has no p2pParameters`() {
        whenever(groupPolicy.get("p2pParameters")).doReturn(null)

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityId = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group has no tlsTrustRoots`() {
        whenever(groupPolicy.get("p2pParameters")).doReturn(emptyMap<String, Any?>())

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityId = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group tlsTrustRoots is empty`() {
        whenever(groupPolicy.get("p2pParameters")).doReturn(mapOf("tlsTrustRoots" to emptyList<Any?>()))

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityId = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group tlsTrustRoots is invalid`() {
        whenever(groupPolicy.get("p2pParameters")).doReturn(mapOf("tlsTrustRoots" to listOf(certificatePem)))

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityId = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyId = null
            )
        }
    }
}
