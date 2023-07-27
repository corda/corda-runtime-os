package net.corda.membership.impl.rest.v1

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.rest.v1.CertificateRestResource.Companion.SIGNATURE_SPEC
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.rest.HttpFileUpload
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.security.auth.x500.X500Principal

class CertificateRestResourceImplTest {
    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private val keyEncodingService = mock<KeyEncodingService>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>() {
        val nodeHoldingIdentity = mock<HoldingIdentity> {
            on { x500Name } doReturn MemberX500Name.parse("O=Alice, L=LDN, C=GB")
        }
        val nodeInfo = mock<VirtualNodeInfo> {
            on { holdingIdentity } doReturn nodeHoldingIdentity
        }
        on { getByHoldingIdentityShortHash(any()) } doReturn nodeInfo
    }
    private val certificatesClient = mock<CertificatesClient>()

    private val certificatesOps = CertificateRestResourceImpl(
        cryptoOpsClient,
        keyEncodingService,
        lifecycleCoordinatorFactory,
        certificatesClient,
        virtualNodeInfoReadService,
        mock()
    )

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `isRunning returns the coordinator status`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            assertThat(certificatesOps.isRunning).isTrue
        }

        @Test
        fun `start starts the coordinator`() {
            certificatesOps.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop stops the coordinator`() {
            certificatesOps.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `UP event will set the status to up`() {
            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            verify(coordinator).updateStatus(LifecycleStatus.UP, "Dependencies are UP")
        }

        @Test
        fun `DOWN event will set the status to down`() {
            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), mock())

            verify(coordinator).updateStatus(LifecycleStatus.DOWN, "Dependencies are DOWN")
        }
    }

    @Nested
    inner class GenerateCsrTests {
        private val holdingIdentityShortHash = "ABA912AC2432"
        private val clusterTenantId = P2P
        private val keyId = "AB0123456789"
        private val x500Name = "CN=Alice"
        private val publicKeyBytes = "123".toByteArray()
        private val key = mock<CryptoSigningKey> {
            on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytes)
            on { schemeCodeName } doReturn ECDSA_SECP256R1_CODE_NAME
            on { tenantId } doReturn holdingIdentityShortHash
        }
        private val publicKey = KeyPairGenerator.getInstance("EC").let { keyPairGenerator ->
            val rnd = mock<SecureRandom> {
                on { nextBytes(any()) } doAnswer {
                    val array = it.arguments[0] as ByteArray
                    array.fill(106)
                }
            }
            keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"), rnd)
            keyPairGenerator.generateKeyPair().public
        }

        @BeforeEach
        fun setUp() {
            val tenantIds = listOf(holdingIdentityShortHash, clusterTenantId)
            tenantIds.forEach { tenantId ->
                whenever(
                    cryptoOpsClient.lookupKeysByIds(
                        tenantId,
                        listOf(ShortHash.of(keyId))
                    )
                ).doReturn(listOf(key))
                whenever(
                    cryptoOpsClient.sign(
                        eq(tenantId),
                        eq(publicKey),
                        argThat<SignatureSpec> { this.signatureName == SignatureSpecs.ECDSA_SHA256.signatureName },
                        any(),
                        eq(emptyMap())
                    )
                ).doReturn(
                    DigitalSignatureWithKey(
                        publicKey,
                        byteArrayOf(1)
                    )
                )
            }
            whenever(keyEncodingService.decodePublicKey(publicKeyBytes)).doReturn(publicKey)
        }

        @Test
        fun `it throws exception if key is not available`() {
            whenever(cryptoOpsClient.lookupKeysByIds(any(), any())).doReturn(emptyList())

            assertThrows<ResourceNotFoundException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    null,
                    null,
                )
            }
        }

        @Test
        fun `it throws ServiceUnavailableException when repartition event happens while trying to retrieve key`() {
            whenever(cryptoOpsClient.lookupKeysByIds(any(), any())).doThrow(CordaRPCAPIPartitionException("repartition event"))

            val details = assertThrows<ServiceUnavailableException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    null,
                    null,
                )
            }

            assertThat(details.message).isEqualTo("Could not find key with ID $keyId for $holdingIdentityShortHash: Repartition Event!")
        }

        @Test
        fun `it sign the request`() {
            certificatesOps.generateCsr(
                holdingIdentityShortHash,
                keyId,
                x500Name,
                null,
                null,
            )

            verify(cryptoOpsClient).sign(
                eq(holdingIdentityShortHash),
                eq(publicKey),
                argThat<SignatureSpec> { this.signatureName == SignatureSpecs.ECDSA_SHA256.signatureName },
                any(),
                eq(emptyMap())
            )
        }

        @Test
        fun `it signs the request for valid cluster tenant`() {
            certificatesOps.generateCsr(
                P2P,
                keyId,
                x500Name,
                null,
                null,
            )

            verify(cryptoOpsClient).sign(
                eq(P2P),
                eq(publicKey),
                argThat<SignatureSpec> { this.signatureName == SignatureSpecs.ECDSA_SHA256.signatureName },
                any(),
                eq(emptyMap())
            )
        }

        @Test
        fun `it returns the correct signature`() {
            val pem = certificatesOps.generateCsr(
                holdingIdentityShortHash,
                keyId,
                x500Name,
                null,
                null,
            )

            assertThat(pem.fromPem().signature).isEqualTo(byteArrayOf(1))
        }

        @Test
        fun `it adds alternative subject names when some are provided`() {
            val pem = certificatesOps.generateCsr(
                holdingIdentityShortHash,
                keyId,
                x500Name,
                listOf("www.alice.net", "alice.net", "10.101.100.65"),
                null,
            )

            assertThat(
                pem.fromPem()
                    .requestedExtensions
                    .getExtension(Extension.subjectAlternativeName)
            ).isEqualTo(
                Extension(
                    Extension.subjectAlternativeName,
                    true,
                    DEROctetString(
                        GeneralNames(
                            arrayOf(
                                GeneralName(GeneralName.dNSName, "www.alice.net"),
                                GeneralName(GeneralName.dNSName, "alice.net"),
                                GeneralName(GeneralName.iPAddress, "10.101.100.65")
                            )
                        )
                    )
                )
            )
        }

        @Test
        fun `it will not adds alternative subject names when none are provided`() {
            val pem = certificatesOps.generateCsr(
                holdingIdentityShortHash,
                keyId,
                x500Name,
                null,
                null,
            )

            assertThat(
                pem.fromPem()
                    .requestedExtensions
                    .getExtension(Extension.subjectAlternativeName)
            ).isNull()
        }

        @Test
        fun `it throw an exception if the subject alternative name is invalid`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    listOf("10.101.100"),
                    null,
                )
            }
        }

        @Test
        fun `it throw an exception if the subject alternative name is empty`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    listOf(""),
                    null,
                )
            }
        }

        @Test
        fun `it throw an exception if the subject alternative name is not a domain name`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    listOf("hello world"),
                    null,
                )
            }
        }

        @Test
        fun `it will use the correct x500 name`() {
            val pem = certificatesOps.generateCsr(
                holdingIdentityShortHash,
                keyId,
                x500Name,
                null,
                emptyMap(),
            )

            assertThat(
                pem.fromPem()
                    .subject
            ).isEqualTo(X500Name(x500Name))
        }

        @Test
        fun `it will throw an exception for invalid X500 name`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    "nop",
                    null,
                    emptyMap(),
                )
            }
        }

        @Test
        fun `it will throw an exception for invalid member name for TLS certificate`() {
            whenever(key.category).doReturn(CryptoConsts.Categories.TLS)

            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    null,
                    emptyMap(),
                )
            }
        }

        @Test
        fun `it will generate a CSR for a valid member name for TLS certificate`() {
            whenever(key.category).doReturn(CryptoConsts.Categories.TLS)

            val csr = certificatesOps.generateCsr(
                holdingIdentityShortHash,
                keyId,
                "O=Alice, L=LDN, C=GB",
                null,
                emptyMap(),
            )

            assertThat(csr).isNotNull
        }

        @Test
        fun `it will throw an exception for invalid name for session certificate`() {
            whenever(key.category).doReturn(CryptoConsts.Categories.SESSION_INIT)

            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    null,
                    emptyMap(),
                )
            }
        }

        @Test
        fun `it will throw an exception for session certificate cluster key where the member can not be found`() {
            whenever(key.category).doReturn(CryptoConsts.Categories.SESSION_INIT)
            whenever(virtualNodeInfoReadService.getAll()).doReturn(emptyList())
            whenever(cryptoOpsClient.lookupKeysByIds(P2P, listOf(ShortHash.of(keyId)))).doReturn(listOf(key))

            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    P2P,
                    keyId,
                    "O=Alice, L=LDN, C=GB",
                    null,
                    emptyMap(),
                )
            }
        }

        @Test
        fun `it will generate CSR for session certificate cluster key where the member can be found`() {
            whenever(key.category).doReturn(CryptoConsts.Categories.SESSION_INIT)
            val nodeHoldingIdentity = mock<HoldingIdentity> {
                on { x500Name } doReturn MemberX500Name.parse("O=Alice, L=LDN, C=GB")
            }
            val nodeInfo = mock<VirtualNodeInfo> {
                on { holdingIdentity } doReturn nodeHoldingIdentity
            }
            whenever(virtualNodeInfoReadService.getAll()).doReturn(listOf(nodeInfo))
            whenever(
                cryptoOpsClient.sign(
                    eq(P2P),
                    eq(publicKey),
                    any<SignatureSpec>(),
                    any(),
                    eq(emptyMap())
                )
            ).doReturn(
                DigitalSignatureWithKey(
                    publicKey,
                    byteArrayOf(1)
                )
            )
            whenever(cryptoOpsClient.lookupKeysByIds(P2P, listOf(ShortHash.of(keyId)))).doReturn(listOf(key))

            val csr = certificatesOps.generateCsr(
                P2P,
                keyId,
                nodeHoldingIdentity.x500Name.toString(),
                null,
                emptyMap(),
            )

            assertThat(csr).isNotNull
        }

        @Test
        fun `it will generate CSR for session certificate member key where the member name is correct`() {
            whenever(key.category).doReturn(CryptoConsts.Categories.SESSION_INIT)
            val tenantId = "123123123123"
            val nodeHoldingIdentity = mock<HoldingIdentity> {
                on { x500Name } doReturn MemberX500Name.parse("O=Alice, L=LDN, C=GB")
            }
            val nodeInfo = mock<VirtualNodeInfo> {
                on { holdingIdentity } doReturn nodeHoldingIdentity
            }
            whenever(
                virtualNodeInfoReadService.getByHoldingIdentityShortHash(
                    ShortHash.of(tenantId)
                )
            ).doReturn(nodeInfo)
            whenever(
                cryptoOpsClient.sign(
                    eq(tenantId),
                    eq(publicKey),
                    any<SignatureSpec>(),
                    any(),
                    eq(emptyMap())
                )
            ).doReturn(
                DigitalSignatureWithKey(
                    publicKey,
                    byteArrayOf(1)
                )
            )
            whenever(cryptoOpsClient.lookupKeysByIds(tenantId, listOf(ShortHash.of(keyId)))).doReturn(listOf(key))

            val csr = certificatesOps.generateCsr(
                tenantId,
                keyId,
                nodeHoldingIdentity.x500Name.toString(),
                null,
                emptyMap(),
            )

            assertThat(csr).isNotNull
        }

        @Test
        fun `it will throw an exception for session certificate member key where the member name is not correct`() {
            whenever(key.category).doReturn(CryptoConsts.Categories.SESSION_INIT)
            val tenantId = "123123123123"
            whenever(
                cryptoOpsClient.sign(
                    eq(tenantId),
                    eq(publicKey),
                    any<SignatureSpec>(),
                    any(),
                    eq(emptyMap())
                )
            ).doReturn(
                DigitalSignatureWithKey(
                    publicKey,
                    byteArrayOf(1)
                )
            )
            whenever(cryptoOpsClient.lookupKeysByIds(tenantId, listOf(ShortHash.of(keyId)))).doReturn(listOf(key))

            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    tenantId,
                    keyId,
                    "O=Bob, L=LDN, C=GB",
                    null,
                    emptyMap(),
                )
            }
        }

        @Test
        fun `it throws exception if Signature OID can not be inferred`() {
            assertThrows<ResourceNotFoundException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    null,
                    mapOf(SIGNATURE_SPEC to "Nop")
                )
            }
        }

        @Test
        fun `it throws exception if key code name is invalid`() {
            whenever(key.schemeCodeName).doReturn("Nop")

            assertThrows<ResourceNotFoundException> {
                certificatesOps.generateCsr(
                    holdingIdentityShortHash,
                    keyId,
                    x500Name,
                    null,
                    null,
                )
            }
        }

        @Test
        fun `it throws exception if tenant ID is not valid`() {
            val invalidTenantId = "XSASD"

            assertThrows<InvalidInputDataException> {
                certificatesOps.generateCsr(
                    invalidTenantId,
                    keyId,
                    x500Name,
                    null,
                    null,
                )
            }
        }

        @Test
        fun `it throws exception if tenant ID does not have virtual node registered`() {
            val notRegisteredTenantId = "ABA912AC2439"
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(ShortHash.of(notRegisteredTenantId))).thenReturn(null)
            assertThrows<ResourceNotFoundException> {
                certificatesOps.generateCsr(
                    notRegisteredTenantId,
                    keyId,
                    x500Name,
                    null,
                    null,
                )
            }
        }

        private fun String.fromPem(): PKCS10CertificationRequest {
            return PEMParser(this.reader()).use { parser ->
                parser.readObject() as PKCS10CertificationRequest
            }
        }
    }

    @Nested
    inner class ImportCertificateChainTests {
        @Test
        fun `invalid certificate will throw an exception`() {
            val certificateText = "hello"
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }

            assertThrows<InvalidInputDataException> {
                certificatesOps.importCertificateChain("rest-tls", null, "alias", listOf(certificate))
            }
        }

        @Test
        fun `valid certificate will send it to the client`() {
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }

            certificatesOps.importCertificateChain("p2p-tls", null, "alias", listOf(certificate))

            verify(certificatesClient).importCertificates(CertificateUsage.P2P_TLS, null, "alias", certificateText)
        }

        @Test
        fun `holding id will be translate to short hash`() {
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }

            certificatesOps.importCertificateChain("p2p-tls", "123123123123", "alias", listOf(certificate))

            verify(certificatesClient).importCertificates(
                CertificateUsage.P2P_TLS,
                ShortHash.of("123123123123"),
                "alias",
                certificateText
            )
        }

        @Test
        fun `invalid usage will throw an exception`() {
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }

            assertThrows<InvalidInputDataException> {
                certificatesOps.importCertificateChain("nop", "123123123123", "alias", listOf(certificate))
            }
        }

        @Test
        fun `no certificates throws an exception`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.importCertificateChain("rest-tls", null, "alias", emptyList())
            }
        }

        @Test
        fun `no actual certificates throws an exception`() {
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn
                    "".toByteArray().inputStream()
            }
            assertThrows<InvalidInputDataException> {
                certificatesOps.importCertificateChain("rest-tls", null, "alias", listOf(certificate))
            }
        }

        @Test
        fun `session certificate will fail if the certificate is a cluster certificate`() {
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }

            assertThrows<InvalidInputDataException> {
                certificatesOps.importCertificateChain("p2p-session", null, "alias", listOf(certificate))
            }
        }

        @Test
        fun `session certificate will fail if the virtual node can not be found`() {
            val shortHash = ShortHash.of("123412341234")
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(shortHash)).thenReturn(null)
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }

            assertThrows<InvalidInputDataException> {
                certificatesOps.importCertificateChain("p2p-session", shortHash.value, "alias", listOf(certificate))
            }
        }

        @Test
        fun `session certificate will fail if the subject is not the member name`() {
            val shortHash = ShortHash.of("123412341234")
            val nodeInfo = mock<VirtualNodeInfo> {
                on { holdingIdentity } doReturn HoldingIdentity(
                    MemberX500Name.parse("O=Alice, L=LDN, C=GB"),
                    "group",
                )
            }
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(shortHash)).thenReturn(nodeInfo)
            val x509Certificate = mock<X509Certificate> {
                on { subjectX500Principal } doReturn X500Principal("O=Bob, L=LDN, C=GB")
            }
            val certificateFactory = mock<CertificateFactory> {
                on { generateCertificates(any()) } doReturn listOf(x509Certificate)
            }
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }
            mockStatic(CertificateFactory::class.java).use {
                it.`when`<CertificateFactory> {
                    CertificateFactory.getInstance("X.509")
                }.doReturn(certificateFactory)

                assertThrows<InvalidInputDataException> {
                    certificatesOps.importCertificateChain("p2p-session", shortHash.value, "alias", listOf(certificate))
                }
            }
        }

        @Test
        fun `session certificate will not fail if the virtual node subject is the member name`() {
            val name = MemberX500Name.parse("O=Alice, L=LDN, C=GB")
            val shortHash = ShortHash.of("123412341234")
            val nodeInfo = mock<VirtualNodeInfo> {
                on { holdingIdentity } doReturn HoldingIdentity(
                    name,
                    "group",
                )
            }
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(shortHash)).thenReturn(nodeInfo)
            val x509Certificate = mock<X509Certificate> {
                on { subjectX500Principal } doReturn name.x500Principal
            }
            val certificateFactory = mock<CertificateFactory> {
                on { generateCertificates(any()) } doReturn listOf(x509Certificate)
            }
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }
            mockStatic(CertificateFactory::class.java).use {
                it.`when`<CertificateFactory> {
                    CertificateFactory.getInstance("X.509")
                }.doReturn(certificateFactory)

                certificatesOps.importCertificateChain("p2p-session", shortHash.value, "alias", listOf(certificate))
            }
        }

        @Test
        fun `session certificate will fail if the certificate name is not a vaild member name`() {
            val shortHash = ShortHash.of("123412341234")
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(shortHash)).thenReturn(mock())
            val x509Certificate = mock<X509Certificate> {
                on { subjectX500Principal } doReturn X500Principal("C=Alice")
            }
            val certificateFactory = mock<CertificateFactory> {
                on { generateCertificates(any()) } doReturn listOf(x509Certificate)
            }
            val certificateText = ""
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }
            mockStatic(CertificateFactory::class.java).use {
                it.`when`<CertificateFactory> {
                    CertificateFactory.getInstance("X.509")
                }.doReturn(certificateFactory)

                assertThrows<InvalidInputDataException> {
                    certificatesOps.importCertificateChain("p2p-session", shortHash.value, "alias", listOf(certificate))
                }
            }
        }

        @Test
        fun `empty alias throws an exception`() {
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }

            val details = assertThrows<InvalidInputDataException> {
                certificatesOps.importCertificateChain("rest-tls", null, "", listOf(certificate))
            }.details
            assertThat(details).containsKey("alias")
        }

        @Test
        fun `valid multiple certificate will send all to the client`() {
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate1 = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }
            val certificate2 = mock<HttpFileUpload> {
                on { content } doReturn ("$certificateText\n$certificateText").byteInputStream()
            }

            certificatesOps.importCertificateChain("rest-tls", null, "alias", listOf(certificate1, certificate2))

            verify(certificatesClient).importCertificates(
                CertificateUsage.REST_TLS,
                null,
                "alias",
                "$certificateText\n$certificateText\n$certificateText"
            )
        }

        @Test
        fun `repartition event during operation throws ServiceUnavailableException`() {
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }
            whenever(certificatesClient.importCertificates(CertificateUsage.P2P_TLS, null, "alias", certificateText))
                .doThrow(CordaRPCAPIPartitionException("repartition event"))

            val details = assertThrows<ServiceUnavailableException> {
                certificatesOps.importCertificateChain("p2p-tls", null, "alias", listOf(certificate))
            }

            assertThat(details.message).isEqualTo("Could not import certificate: Repartition Event!")
        }
    }

    @Nested
    inner class GetCertificateAliasesTests {
        @Test
        fun `it throws exception for invalid short hash`() {
            assertThrows<BadRequestException> {
                certificatesOps.getCertificateAliases(
                    CertificateUsage.REST_TLS.publicName,
                    "nop"
                )
            }
        }

        @Test
        fun `it throws exception for bad usage`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.getCertificateAliases(
                    "nop",
                    "012301230123"
                )
            }
        }

        @Test
        fun `it calls the client with the correct arguments`() {
            whenever(certificatesClient.getCertificateAliases(any(), any())).doReturn(emptyList())

            certificatesOps.getCertificateAliases(
                CertificateUsage.REST_TLS.publicName,
                "012301230123",
            )

            verify(certificatesClient).getCertificateAliases(
                CertificateUsage.REST_TLS,
                ShortHash.of("012301230123")
            )
        }

        @Test
        fun `it return the correct data`() {
            whenever(certificatesClient.getCertificateAliases(any(), anyOrNull())).doReturn(listOf("one", "two"))

            val aliases = certificatesOps.getCertificateAliases(
                CertificateUsage.REST_TLS.publicName,
                null,
            )

            assertThat(aliases).containsExactlyInAnyOrder("one", "two")
        }

        @Test
        fun `it throws an exception if the request fails`() {
            whenever(certificatesClient.getCertificateAliases(any(), any())).doThrow(CordaRuntimeException("Ooops"))

            assertThrows<InternalServerException> {
                certificatesOps.getCertificateAliases(
                    CertificateUsage.REST_TLS.publicName,
                    "012301230123",
                )
            }
        }

        @Test
        fun `it throws an exception if repartition event occurs while waiting for response`() {
            whenever(certificatesClient.getCertificateAliases(any(), any())).doThrow(CordaRPCAPIPartitionException("repartition"))

            val details = assertThrows<ServiceUnavailableException> {
                certificatesOps.getCertificateAliases(
                    CertificateUsage.REST_TLS.publicName,
                    "012301230123",
                )
            }

            assertThat(details.message).isEqualTo("Could not get certificate aliases: Repartition Event!")
        }
    }

    @Nested
    inner class GetCertificateChainTests {
        @Test
        fun `it throws an exception for empty alias`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.getCertificateChain(
                    CertificateUsage.REST_TLS.publicName,
                    null,
                    "  "
                )
            }
        }

        @Test
        fun `it throws an exception for invalid holding ID`() {
            assertThrows<BadRequestException> {
                certificatesOps.getCertificateChain(
                    CertificateUsage.REST_TLS.publicName,
                    " ",
                    "alias"
                )
            }
        }

        @Test
        fun `it throws an exception for invalid usage`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.getCertificateChain(
                    "nop",
                    null,
                    "alias"
                )
            }
        }

        @Test
        fun `it throws an exception if alias can not be found`() {
            whenever(certificatesClient.retrieveCertificates(null, CertificateUsage.REST_TLS, "alias")).doReturn(null)
            assertThrows<ResourceNotFoundException> {
                certificatesOps.getCertificateChain(
                    CertificateUsage.REST_TLS.publicName,
                    null,
                    "alias"
                )
            }
        }

        @Test
        fun `it return the correct data if the alias was found`() {
            val hash = "321432143214"
            val usage = CertificateUsage.P2P_SESSION
            val pemCertificate = "yep"
            val alias = "alias"
            whenever(certificatesClient.retrieveCertificates(ShortHash.of(hash), usage, alias)).doReturn(pemCertificate)

            val certificate = certificatesOps.getCertificateChain(
                usage.publicName,
                hash,
                alias,
            )

            assertThat(certificate).isEqualTo(pemCertificate)
        }

        @Test
        fun `it throws an exception if the client had an error`() {
            whenever(
                certificatesClient.retrieveCertificates(null, CertificateUsage.P2P_SESSION, "alias")
            ).doThrow(CordaRuntimeException("Ooops"))

            assertThrows<InternalServerException> {
                certificatesOps.getCertificateChain(
                    CertificateUsage.P2P_SESSION.publicName,
                    null,
                    "alias",
                )
            }
        }


        @Test
        fun `it throws an exception if repartition event occurs while waiting for response`() {
            whenever(certificatesClient.retrieveCertificates(null, CertificateUsage.P2P_SESSION, "alias"))
                .doThrow(CordaRPCAPIPartitionException("repartition"))

            val details = assertThrows<ServiceUnavailableException> {
                certificatesOps.getCertificateChain(
                    CertificateUsage.P2P_SESSION.publicName,
                    null,
                    "alias",
                )
            }

            assertThat(details.message).isEqualTo("Could not get certificate chain: Repartition Event!")
        }
    }
}
