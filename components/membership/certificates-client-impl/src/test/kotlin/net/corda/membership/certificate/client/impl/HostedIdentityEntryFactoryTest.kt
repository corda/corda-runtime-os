package net.corda.membership.certificate.client.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.schema.Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
        const val VALID_CHAIN_CERTIFICATE_ALIAS = "chain-alias"
        const val WRONG_SIGN_CERTIFICATE_ALIAS = "wrong"
        val SESSION_KEY_ID = ShortHash.of("AB0123456789")
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
    private val wrongSignCertificatePem =
        HostedIdentityEntryFactoryTest::class.java.getResource("/certificates/wrong-sign-certificate.pem")!!.readText()
            .replace("\r", "")
            .replace("\n", System.lineSeparator())

    private val rootPem = HostedIdentityEntryFactoryTest::class.java.getResource("/certificates/root.pem")!!.readText()
    private val chainRootPem = HostedIdentityEntryFactoryTest::class.java.getResource("/certificates/chain/ca.pem")!!.readText()
        .replace("\r", "")
        .replace("\n", System.lineSeparator())
    private val chainCertificatesPems = (0..3).map { index ->
        HostedIdentityEntryFactoryTest::class.java.getResource("/certificates/chain/certificate.$index.pem")!!.readText()
            .replace("\r", "")
            .replace("\n", System.lineSeparator())
    }
    private val certificatePublicKey = certificatePem.let {
        CertificateFactory.getInstance("X.509").generateCertificate(it.byteInputStream()).publicKey
    }
    private val wrongSignCertificatePublicKey = wrongSignCertificatePem.let {
        CertificateFactory.getInstance("X.509").generateCertificate(it.byteInputStream()).publicKey
    }
    private val chainCertificatePublicKey = chainCertificatesPems.first().let {
        CertificateFactory.getInstance("X.509").generateCertificate(it.byteInputStream()).publicKey
    }
    private val filter = argumentCaptor<Map<String, String>>()
    private val ids = argumentCaptor<List<ShortHash>>()
    private val cryptoOpsClient = mock<CryptoOpsClient> {
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
        on {
            filterMyKeys(eq(P2P), eq(listOf(chainCertificatePublicKey)), any())
        }.doReturn(listOf(chainCertificatePublicKey))
        on {
            filterMyKeys(eq(VALID_NODE.toString()), eq(listOf(chainCertificatePublicKey)), any())
        }.doReturn(listOf(chainCertificatePublicKey))
        on {
            filterMyKeys(eq(VALID_NODE.toString()), eq(listOf(wrongSignCertificatePublicKey)), any())
        }.doReturn(listOf(wrongSignCertificatePublicKey))
        on {
            filterMyKeys(eq(P2P), eq(listOf(wrongSignCertificatePublicKey)), any())
        }.doReturn(listOf(wrongSignCertificatePublicKey))
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
        on { tlsTrustRoots } doReturn listOf(rootPem, chainRootPem)
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
        when (alias) {
            VALID_CERTIFICATE_ALIAS -> certificatePem
            WRONG_SIGN_CERTIFICATE_ALIAS -> wrongSignCertificatePem
            VALID_CHAIN_CERTIFICATE_ALIAS -> chainCertificatesPems.joinToString(System.lineSeparator())
            else -> null
        }
    }

    @Test
    fun `createIdentityRecord create the correct record`() {
        whenever(groupPolicy.p2pParameters).doReturn(p2pParamsSessionPki)
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            ),
            useClusterLevelTlsCertificateAndKey = false,
            alternativeSessionKeyAndCertificates = emptyList(),
        )

        assertSoftly { softly ->
            softly.assertThat(record.topic).isEqualTo(P2P_HOSTED_IDENTITIES_TOPIC)
            softly.assertThat(record.key).isEqualTo(validHoldingId.shortHash.value)
            softly.assertThat(record.value).isEqualTo(
                HostedIdentityEntry(
                    validHoldingId.toAvro(),
                    VALID_NODE.toString(),
                    listOf(certificatePem),
                    HostedIdentitySessionKeyAndCert(
                        PUBLIC_KEY_PEM,
                        listOf(certificatePem)
                    ),
                    emptyList(),
                    1
                )
            )
        }
    }

    @Test
    fun `createIdentityRecord create the correct record if alternative keys are used`() {
        val keys = (1..3).map {
            val pem = "00001111222$it"
            val encoded = ByteBuffer.wrap(pem.toByteArray())
            val keyId = ShortHash.of(pem)
            val key = mock<PublicKey>()
            val sessionKey = mock<CryptoSigningKey> {
                on { publicKey } doReturn encoded
            }
            whenever(keyEncodingService.encodeAsString(key)).doReturn(pem)
            whenever(keyEncodingService.decodePublicKey(pem.toByteArray())).doReturn(key)
            whenever(
                cryptoOpsClient.lookupKeysByIds(
                    eq(VALID_NODE.toString()),
                    eq(listOf(keyId))
                )
            ).doReturn(listOf(sessionKey))
            keyId to pem
        }

        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            useClusterLevelTlsCertificateAndKey = false,
            alternativeSessionKeyAndCertificates = keys.map { (keyId, _) ->
                CertificatesClient.SessionKeyAndCertificate(
                    keyId,
                    null,
                )
            },
        )

        assertThat(record.value?.alternativeSessionKeysAndCerts).containsAnyElementsOf(
            keys.map { (_, pem) ->
                HostedIdentitySessionKeyAndCert(pem, null)
            }
        )
    }

    @Test
    fun `createIdentityRecord will throw an exception for invalid node`() {
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = INVALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
            )
        }
    }

    @Test
    fun `createIdentityRecord will use virtual node tenant ID if asked for`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            alternativeSessionKeyAndCertificates = emptyList()
        )

        assertThat(record.value?.preferredSessionKeyAndCert?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will call keepSubjectIfNeeded if needed`() {
        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            alternativeSessionKeyAndCertificates = emptyList(),
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
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
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
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            alternativeSessionKeyAndCertificates = emptyList()
        )

        assertThat(record.value?.preferredSessionKeyAndCert?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will use cluster tenant ID if provided`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            alternativeSessionKeyAndCertificates = emptyList(),
        )

        assertThat(record.value?.preferredSessionKeyAndCert?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will default to holding identity short hash if session tenant ID is not provided`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            alternativeSessionKeyAndCertificates = emptyList(),
        )

        assertThat(record.value?.preferredSessionKeyAndCert?.sessionPublicKey).isEqualTo(PUBLIC_KEY_PEM)
    }

    @Test
    fun `createIdentityRecord will look for ID if provided`() {
        factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = false,
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            alternativeSessionKeyAndCertificates = emptyList(),
        )

        assertThat(ids.firstValue)
            .hasSize(1)
            .contains(SESSION_KEY_ID)
    }

    @Test
    fun `createIdentityRecord will throw an exception if session key can not be found`() {
        whenever(
            cryptoOpsClient.lookupKeysByIds(
                eq(VALID_NODE.toString()),
                eq(listOf(SESSION_KEY_ID)),
            )
        ).doReturn(emptyList())
        assertThrows<CertificatesResourceNotFoundException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
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
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
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
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = "NOP",
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
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
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            alternativeSessionKeyAndCertificates = emptyList(),
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
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
            ),
            alternativeSessionKeyAndCertificates = emptyList(),
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
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            alternativeSessionKeyAndCertificates = emptyList(),
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
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
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
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),

                alternativeSessionKeyAndCertificates = emptyList(),
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group tlsTrustRoots is empty`() {
        whenever(p2pParams.tlsTrustRoots).doReturn(emptyList())

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group tlsTrustRoots is invalid`() {
        whenever(p2pParams.tlsTrustRoots).doReturn(listOf(certificatePem))

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the tlsTrustRoots certificate signature is wrong`() {
        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = WRONG_SIGN_CERTIFICATE_ALIAS,
                useClusterLevelTlsCertificateAndKey = false,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
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
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
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
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
                useClusterLevelTlsCertificateAndKey = true,
            )
        }
    }

    @Test
    fun `createIdentityRecord will throw an exception if the group sessionTrustRoots is sign wrongly`() {
        val p2pParams: GroupPolicy.P2PParameters = mock {
            on { tlsTrustRoots } doReturn listOf(rootPem)
            on { sessionTrustRoots } doReturn listOf(rootPem)
        }
        whenever(groupPolicy.p2pParameters) doReturn p2pParams

        assertThrows<CordaRuntimeException> {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CERTIFICATE_ALIAS,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = WRONG_SIGN_CERTIFICATE_ALIAS,
                ),
                alternativeSessionKeyAndCertificates = emptyList(),
                useClusterLevelTlsCertificateAndKey = true,
            )
        }
    }

    @Test
    fun `valid certificate chain is created when needed`() {
        val record = factory.createIdentityRecord(
            holdingIdentityShortHash = VALID_NODE,
            tlsCertificateChainAlias = VALID_CHAIN_CERTIFICATE_ALIAS,
            preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                sessionKeyId = SESSION_KEY_ID,
                sessionCertificateChainAlias = null,
            ),
            useClusterLevelTlsCertificateAndKey = false,
            alternativeSessionKeyAndCertificates = emptyList(),
        )

        assertThat(record.value?.tlsCertificates).isEqualTo(chainCertificatesPems)
    }

    @Test
    fun `invalid certificate chain due to wrong order will fail`() {
        val factory = HostedIdentityEntryFactory(
            virtualNodeInfoReadService,
            cryptoOpsClient,
            keyEncodingService,
            groupPolicyProvider,
            mtlsMgmClientCertificateKeeper,
        ) { _, _, _ ->
            chainCertificatesPems.reversed().joinToString(separator = System.lineSeparator())
        }
        assertThatThrownBy {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CHAIN_CERTIFICATE_ALIAS,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                useClusterLevelTlsCertificateAndKey = false,
                alternativeSessionKeyAndCertificates = emptyList(),
            )
        }.hasMessageContaining("The previous certificate in the chain was issued by")
    }

    @Test
    fun `invalid certificate chain due to missing first certificate`() {
        val factory = HostedIdentityEntryFactory(
            virtualNodeInfoReadService,
            cryptoOpsClient,
            keyEncodingService,
            groupPolicyProvider,
            mtlsMgmClientCertificateKeeper,
        ) { _, _, _ ->
            chainCertificatesPems.drop(1).joinToString(separator = System.lineSeparator())
        }
        assertThatThrownBy {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CHAIN_CERTIFICATE_ALIAS,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                useClusterLevelTlsCertificateAndKey = false,
                alternativeSessionKeyAndCertificates = emptyList(),
            )
        }.hasMessageContaining("This certificate public key is unknown to")
    }

    @Test
    fun `invalid certificate chain due to certificate not sign by ca`() {
        val factory = HostedIdentityEntryFactory(
            virtualNodeInfoReadService,
            cryptoOpsClient,
            keyEncodingService,
            groupPolicyProvider,
            mtlsMgmClientCertificateKeeper,
        ) { _, _, _ ->
            chainCertificatesPems.take(2).joinToString(separator = System.lineSeparator())
        }
        assertThatThrownBy {
            factory.createIdentityRecord(
                holdingIdentityShortHash = VALID_NODE,
                tlsCertificateChainAlias = VALID_CHAIN_CERTIFICATE_ALIAS,
                preferredSessionKeyAndCertificate = CertificatesClient.SessionKeyAndCertificate(
                    sessionKeyId = SESSION_KEY_ID,
                    sessionCertificateChainAlias = null,
                ),
                useClusterLevelTlsCertificateAndKey = false,
                alternativeSessionKeyAndCertificates = emptyList(),
            )
        }.hasMessageContaining("The TLS certificate was not signed by the correct certificate authority")
    }
}
