package net.corda.membership.certificate.client.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.exceptions.CordaRuntimeException
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.cert.CertificateFactory

class HostedIdentityEntryFactoryTest {
    private companion object {
        val validHoldingId = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group-1")
        val publicKeyBytes = "123".toByteArray()
        val clusterPublicKeyBytes = "456".toByteArray()
        val VALID_NODE = ShortHash.of("1234567890ab")
        val INVALID_NODE = ShortHash.of("deaddeaddead")
        const val PUBLIC_KEY_PEM = "publicKeyPem"
        const val PUBLIC_CLUSTER_KEY_PEM = "publicClusterKeyPem"
        const val VALID_CERTIFICATE_ALIAS = "alias"
        const val SESSION_KEY_ID = "AB0123456789"
    }

    private val nodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn validHoldingId
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(VALID_NODE) } doReturn nodeInfo
        on { getByHoldingIdentityShortHash(INVALID_NODE) } doReturn null
    }
    private val sessionKey = mock<CryptoSigningKey> {
        on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytes)
    }
    private val clusterSessionKey = mock<CryptoSigningKey> {
        on { publicKey } doReturn ByteBuffer.wrap(clusterPublicKeyBytes)
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
    private val ids = argumentCaptor<List<ShortHash>>()
    private val cryptoOpsClient = mock<CryptoOpsClient> {
        on {
            lookup(
                eq(VALID_NODE.toString()),
                eq(0),
                eq(1),
                eq(CryptoKeyOrderBy.NONE),
                filter.capture()
            )
        } doReturn listOf(sessionKey)
        on {
            lookup(
                eq(P2P),
                eq(0),
                eq(1),
                eq(CryptoKeyOrderBy.NONE),
                filter.capture()
            )
        } doReturn listOf(clusterSessionKey)
        on {
            lookupKeysByIds(
                eq(VALID_NODE.toString()),
                ids.capture()
            )
        } doReturn listOf(sessionKey)

        on {
            filterMyKeys(eq(VALID_NODE.toString()), eq(listOf(certificatePublicKey)), any())
        }.doReturn(listOf(certificatePublicKey))
        on {
            filterMyKeys(eq(P2P), eq(listOf(certificatePublicKey)), any())
        }.doReturn(listOf(certificatePublicKey))
    }
    private val sessionPublicKey = mock<PublicKey>()
    private val clusterSessionPublicKey = mock<PublicKey>()
    private val keyEncodingService = mock<KeyEncodingService> {
        on { decodePublicKey(publicKeyBytes) } doReturn sessionPublicKey
        on { decodePublicKey(clusterPublicKeyBytes) } doReturn clusterSessionPublicKey
        on { encodeAsString(sessionPublicKey) } doReturn PUBLIC_KEY_PEM
        on { encodeAsString(clusterSessionPublicKey) } doReturn PUBLIC_CLUSTER_KEY_PEM
    }
    private val p2pParamsSessionPki: GroupPolicy.P2PParameters = mock {
        on { tlsTrustRoots } doReturn listOf(rootPem)
        on { sessionPki } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD
        on { sessionTrustRoots } doReturn listOf(rootPem)
    }
    private val p2pParams: GroupPolicy.P2PParameters = mock {
        on { tlsTrustRoots } doReturn listOf(rootPem)
        on { sessionPki } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
    }
    private val groupPolicy = mock<GroupPolicy> {
        on { p2pParameters } doReturn p2pParams
    }
    private val groupPolicyProvider = mock<GroupPolicyProvider> {
        on { getGroupPolicy(validHoldingId) } doReturn groupPolicy
    }
    private val mtlsMgmClientCertificateKeeper = mock<MtlsMgmClientCertificateKeeper>()

    private val factory = HostedIdentityEntryFactory(
        virtualNodeInfoReadService,
        cryptoOpsClient,
        keyEncodingService,
        groupPolicyProvider,
        mtlsMgmClientCertificateKeeper,
    ) { _, _, alias ->
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
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            sessionKeyId = null,
            sessionCertificateChainAlias = null
        )

        assertSoftly { softly ->
            softly.assertThat(record.topic).isEqualTo(P2P_HOSTED_IDENTITIES_TOPIC)
            softly.assertThat(record.key).isEqualTo(validHoldingId.shortHash.value)
            softly.assertThat(record.value).isEqualTo(
                HostedIdentityEntry(
                    validHoldingId.toAvro(),
                    VALID_NODE.toString(),
                    listOf(certificatePem),
                    PUBLIC_KEY_PEM,
                    null
                )
            )
        }
    }

    @Test
    fun `createIdentityRecord create the correct record if session certificate used`() {
        whenever(groupPolicy.p2pParameters).doReturn(p2pParamsSessionPki)
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            sessionKeyId = null,
            sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
        )

        assertSoftly { softly ->
            softly.assertThat(record.topic).isEqualTo(P2P_HOSTED_IDENTITIES_TOPIC)
            softly.assertThat(record.key).isEqualTo(validHoldingId.shortHash.value)
            softly.assertThat(record.value).isEqualTo(
                HostedIdentityEntry(
                    validHoldingId.toAvro(),
                    VALID_NODE.toString(),
                    listOf(certificatePem),
                    PUBLIC_KEY_PEM,
                    listOf(certificatePem)
                )
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception for invalid node`() {
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = INVALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                sessionKeyId = null,
                sessionCertificateChainAlias = null,
            )
        }
    }

    @Test
    fun `createIdentityRecord will use virtual node tenant ID if asked for`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            sessionKeyId = SESSION_KEY_ID,
            sessionCertificateChainAlias = null,
        )

        assertThat(record.value?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will call keepSubjectIfNeeded if needed`() {
        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            sessionKeyId = SESSION_KEY_ID,
            sessionCertificateChainAlias = null,
        )

        verify(mtlsMgmClientCertificateKeeper).addMgmCertificateSubjectToGroupPolicy(
            validHoldingId,
            groupPolicy,
            certificatePem,
        )
    }

    @Test
    fun `createIdentityRecord will throw an exception if standard session pki and no sessionCertificateChainAlias specified`() {
        whenever(groupPolicy.p2pParameters).doReturn(p2pParamsSessionPki)
        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                sessionKeyId = null,
                sessionCertificateChainAlias = null,
                useClusterLevelTlsCertificateAndKey = false,
            )
        }
    }

    @Test
    fun `createIdentityRecord will use session tenant ID if provided`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            sessionKeyId = SESSION_KEY_ID,
            sessionCertificateChainAlias = null,
        )

        assertThat(record.value?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will use cluster tenant ID if provided`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            sessionKeyId = null,
            sessionCertificateChainAlias = null,
        )

        assertThat(record.value?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will default to holding identity short hash if session tenant ID is not provided`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            sessionKeyId = SESSION_KEY_ID,
            sessionCertificateChainAlias = null,
        )

        assertThat(record.value?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will look for ID if provided`() {
        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            sessionKeyId = SESSION_KEY_ID,
            sessionCertificateChainAlias = null,
        )

        assertThat(ids.firstValue)
            .hasSize(1)
            .contains(ShortHash.of(SESSION_KEY_ID))
    }

    @Test
    fun `createIdentityRecord will filter by category if no id is provided`() {
        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            sessionKeyId = null,
            sessionCertificateChainAlias = null,
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
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                sessionKeyId = null,
                sessionCertificateChainAlias = null,
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if tls certificates can not be found`() {
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = "NOP",
                useClusterLevelTlsCertificateAndKey = false,
                sessionKeyId = null,
                sessionCertificateChainAlias = null,
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if session certificates can not be found`() {
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                sessionKeyId = null,
                sessionCertificateChainAlias = "NOP",
            )
        }
    }

    @Test
    fun `createIdentityRecord with another TLS tenant will call the certificates from that tenant`() {
        var usage: CertificateUsage? = null
        val factory = HostedIdentityEntryFactory(
            virtualNodeInfoReadService,
            cryptoOpsClient,
            keyEncodingService,
            groupPolicyProvider,
            mock(),
        ) { _, tenant, _ ->
            usage = tenant
            certificatePem
        }

        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = true,
            sessionKeyId = null,
            sessionCertificateChainAlias = null,
        )

        assertThat(usage).isEqualTo(CertificateUsage.P2P_TLS)
    }

    @Test
    fun `createIdentityRecord with another session tenant will call the certificates from that tenant`() {
        whenever(groupPolicy.p2pParameters).doReturn(p2pParamsSessionPki)
        val tenantId = mutableListOf<ShortHash?>()
        whenever(cryptoOpsClient.filterMyKeys(eq(VALID_NODE.toString()), any(), any())).doReturn(listOf(certificatePublicKey))

        val factory = HostedIdentityEntryFactory(
            virtualNodeInfoReadService,
            cryptoOpsClient,
            keyEncodingService,
            groupPolicyProvider,
            mock(),
        ) { tenant, _, _ ->
            tenantId.add(tenant)
            certificatePem
        }
        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            sessionKeyId = SESSION_KEY_ID,
            sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
        )

        assertThat(tenantId).contains(VALID_NODE)
    }

    @Test
    fun `createIdentityRecord with another TLS tenant will return that ID`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = true,
            sessionKeyId = null,
            sessionCertificateChainAlias = null,
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
            mock(),
        ) { _, _, _ ->
            "\n"
        }

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                sessionKeyId = null,
                sessionCertificateChainAlias = null,
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the certificate public key is unknown`() {
        whenever(cryptoOpsClient.filterMyKeys(any(), any(), any())).doReturn(emptyList())

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                sessionKeyId = null,
                sessionCertificateChainAlias = null,
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
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                sessionKeyId = null,
                sessionCertificateChainAlias = null,
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
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                sessionKeyId = null,
                sessionCertificateChainAlias = null,
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group sessionTrustRoots is empty`() {
        val p2pParams: GroupPolicy.P2PParameters = mock {
            on { tlsTrustRoots } doReturn listOf(rootPem)
            on { sessionTrustRoots } doReturn emptyList()
        }
        whenever(groupPolicy.p2pParameters) doReturn p2pParams

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                sessionKeyId = null,
                sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = true,
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group sessionTrustRoots is invalid`() {
        val p2pParams: GroupPolicy.P2PParameters = mock {
            on { tlsTrustRoots } doReturn listOf(rootPem)
            on { sessionTrustRoots } doReturn listOf(certificatePem)
        }
        whenever(groupPolicy.p2pParameters) doReturn p2pParams

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                sessionKeyId = null,
                sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = true,
            )
        }
    }
}
