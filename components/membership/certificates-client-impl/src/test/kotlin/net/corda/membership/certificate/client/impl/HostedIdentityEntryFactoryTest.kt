package net.corda.membership.certificate.client.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.ShortHash
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
        val validHoldingId = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group-1")
        val publicKeyBytes = "123".toByteArray()
        val publicKeyBytesKnownTenant = "456".toByteArray()
        const val VALID_NODE = "1234567890ab"
        const val INVALID_NODE = "deaddeaddead"
        const val PUBLIC_KEY_PEM = "publicKeyPem"
        const val KNOWN_TENANT_PUBLIC_KEY_PEM = "knownTenantPublicKeyPem"
        const val VALID_CERTIFICATE_ALIAS = "alias"
        const val KNOWN_TENANT = "sessionKeyTenantId"
    }

    private val nodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn validHoldingId
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(ShortHash.of(VALID_NODE)) } doReturn nodeInfo
        on { getByHoldingIdentityShortHash(ShortHash.of(INVALID_NODE)) } doReturn null
    }
    private val sessionKey = mock<CryptoSigningKey> {
        on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytes)
    }
    private val knownTenantSessionKey = mock<CryptoSigningKey> {
        on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytesKnownTenant)
    }
    private val certificatePem =
        HostedIdentityEntryFactoryTest::class.java.getResource("/certificates/$VALID_NODE.pem")!!.readText()
            .replace("\r", "")
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
            lookup(
                eq(KNOWN_TENANT),
                ids.capture()
            )
        } doReturn listOf(knownTenantSessionKey)

        on {
            filterMyKeys(eq(VALID_NODE), eq(listOf(certificatePublicKey)))
        }.doReturn(listOf(certificatePublicKey))
        on {
            filterMyKeys(eq(P2P), eq(listOf(certificatePublicKey)))
        }.doReturn(listOf(certificatePublicKey))
    }
    private val sessionPublicKey = mock<PublicKey>()
    private val knownTenantSessionPublicKey = mock<PublicKey>()
    private val keyEncodingService = mock<KeyEncodingService> {
        on { decodePublicKey(publicKeyBytes) } doReturn sessionPublicKey
        on { decodePublicKey(publicKeyBytesKnownTenant) } doReturn knownTenantSessionPublicKey
        on { encodeAsString(sessionPublicKey) } doReturn PUBLIC_KEY_PEM
        on { encodeAsString(knownTenantSessionPublicKey) } doReturn KNOWN_TENANT_PUBLIC_KEY_PEM
    }
    private val p2pParams: GroupPolicy.P2PParameters = mock {
        on { tlsTrustRoots } doReturn listOf(rootPem)
    }
    private val groupPolicy = mock<GroupPolicy> {
        on { p2pParameters } doReturn p2pParams
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
            holdingIdentityShortHash = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = null,
            sessionKeyTenantId = null,
            sessionKeyId = null
        )

        assertSoftly { softly ->
            softly.assertThat(record.topic).isEqualTo(P2P_HOSTED_IDENTITIES_TOPIC)
            softly.assertThat(record.key).isEqualTo(validHoldingId.shortHash.value)
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
                holdingIdentityShortHash = INVALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = VALID_NODE,
                sessionKeyTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will use session tenant ID if provided`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = VALID_NODE,
            sessionKeyTenantId = KNOWN_TENANT,
            sessionKeyId = "id1"
        )

        assertThat(record.value?.sessionKeyTenantId).isEqualTo(KNOWN_TENANT)
        assertThat(record.value?.sessionPublicKey).isEqualTo(KNOWN_TENANT_PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will default to holding identity short hash if session tenant ID is not provided`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = VALID_NODE,
            sessionKeyTenantId = null,
            sessionKeyId = "id1"
        )

        assertThat(record.value?.sessionKeyTenantId).isEqualTo(VALID_NODE)
        assertThat(record.value?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will look for ID if provided`() {
        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = VALID_NODE,
            sessionKeyTenantId = null,
            sessionKeyId = "id1"
        )

        assertThat(ids.firstValue)
            .hasSize(1)
            .contains("id1")
    }

    @Test
    fun `createIdentityRecord will filter by category if no id is provided`() {
        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = VALID_NODE,
            sessionKeyTenantId = null,
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
                holdingIdentityShortHash = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = VALID_NODE,
                sessionKeyTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if certificates can not be found`() {
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                certificateChainAlias = "NOP",
                tlsTenantId = VALID_NODE,
                sessionKeyTenantId = null,
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
            holdingIdentityShortHash = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = P2P,
            sessionKeyTenantId = null,
            sessionKeyId = null
        )

        assertThat(tenantId).isEqualTo("p2p")
    }

    @Test
    fun `createIdentityRecord with another TLS tenant will return that ID`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            certificateChainAlias = VALID_CERTIFICATE_ALIAS,
            tlsTenantId = P2P,
            sessionKeyTenantId = null,
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
                holdingIdentityShortHash = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the certificate public key is unknown`() {
        whenever(cryptoOpsClient.filterMyKeys(any(), any())).doReturn(emptyList())

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group tlsTrustRoots is empty`() {
        val p2pParams: GroupPolicy.P2PParameters = mock {
            on { tlsTrustRoots } doReturn emptyList()
        }
        whenever(groupPolicy.p2pParameters) doReturn p2pParams

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyTenantId = null,
                sessionKeyId = null
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group tlsTrustRoots is invalid`() {
        val p2pParams: GroupPolicy.P2PParameters = mock {
            on { tlsTrustRoots } doReturn listOf(certificatePem)
        }
        whenever(groupPolicy.p2pParameters) doReturn p2pParams

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                certificateChainAlias = VALID_CERTIFICATE_ALIAS,
                tlsTenantId = null,
                sessionKeyTenantId = null,
                sessionKeyId = null
            )
        }
    }
}
