package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.certificates.CertificateUsage
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.httprpc.v1.CertificatesRpcOps.Companion.SIGNATURE_SPEC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.SignatureSpec.Companion.ECDSA_SHA256
import net.corda.virtualnode.ShortHash
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
import java.security.spec.ECGenParameterSpec

class CertificatesRpcOpsImplTest {
    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private val keyEncodingService = mock<KeyEncodingService>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val certificatesClient = mock<CertificatesClient>()

    private val certificatesOps = CertificatesRpcOpsImpl(
        cryptoOpsClient,
        keyEncodingService,
        lifecycleCoordinatorFactory,
        certificatesClient,
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
        private val holdingIdentityShortHash = "id"
        private val keyId = "keyId"
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
            whenever(cryptoOpsClient.lookup(holdingIdentityShortHash, listOf(keyId))).doReturn(listOf(key))
            whenever(
                cryptoOpsClient.sign(
                    eq(holdingIdentityShortHash),
                    eq(publicKey),
                    argThat<SignatureSpec> { this.signatureName == ECDSA_SHA256.signatureName },
                    any(),
                    eq(emptyMap())
                )
            ).doReturn(
                DigitalSignature.WithKey(
                    publicKey,
                    byteArrayOf(1),
                    emptyMap()
                )
            )
            whenever(keyEncodingService.decodePublicKey(publicKeyBytes)).doReturn(publicKey)
        }

        @Test
        fun `it throws exception if key is not available`() {
            whenever(cryptoOpsClient.lookup(any(), any())).doReturn(emptyList())

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
                argThat<SignatureSpec> { this.signatureName == ECDSA_SHA256.signatureName },
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
                certificatesOps.importCertificateChain("rpc-api-tls", null, "alias", listOf(certificate))
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
                certificatesOps.importCertificateChain("rpc-api-tls", null, "alias", emptyList())
            }
        }

        @Test
        fun `empty alias throws an exception`() {
            val certificateText = ClassLoader.getSystemResource("r3.pem").readText()
            val certificate = mock<HttpFileUpload> {
                on { content } doReturn certificateText.byteInputStream()
            }

            val details = assertThrows<InvalidInputDataException> {
                certificatesOps.importCertificateChain("rpc-api-tls", null, "", listOf(certificate))
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

            certificatesOps.importCertificateChain("rpc-api-tls", null, "alias", listOf(certificate1, certificate2))

            verify(certificatesClient).importCertificates(
                CertificateUsage.RPC_API_TLS,
                null,
                "alias",
                "$certificateText\n$certificateText\n$certificateText"
            )
        }
    }

    @Nested
    inner class GetCertificateAliasesTests {
        @Test
        fun `it throws exception for invalid short hash`() {
            assertThrows<BadRequestException> {
                certificatesOps.getCertificateAliases(
                    CertificateUsage.RPC_API_TLS.publicName,
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
                CertificateUsage.RPC_API_TLS.publicName,
                "012301230123",
            )

            verify(certificatesClient).getCertificateAliases(
                CertificateUsage.RPC_API_TLS,
                ShortHash.of("012301230123")
            )
        }

        @Test
        fun `it return the correct data`() {
            whenever(certificatesClient.getCertificateAliases(any(), anyOrNull())).doReturn(listOf("one", "two"))

            val aliases = certificatesOps.getCertificateAliases(
                CertificateUsage.RPC_API_TLS.publicName,
                null,
            )

            assertThat(aliases).containsExactlyInAnyOrder("one", "two")
        }

        @Test
        fun `it throws an exception if the request fails`() {
            whenever(certificatesClient.getCertificateAliases(any(), any())).doThrow(CordaRuntimeException("Ooops"))

            assertThrows<InternalServerException> {
                certificatesOps.getCertificateAliases(
                    CertificateUsage.RPC_API_TLS.publicName,
                    "012301230123",
                )
            }
        }
    }

    @Nested
    inner class GetCertificateChainTests {
        @Test
        fun `it throws an exception for empty alias`() {
            assertThrows<InvalidInputDataException> {
                certificatesOps.getCertificateChain(
                    CertificateUsage.RPC_API_TLS.publicName,
                    null,
                    "  "
                )
            }
        }

        @Test
        fun `it throws an exception for invalid holding ID`() {
            assertThrows<BadRequestException> {
                certificatesOps.getCertificateChain(
                    CertificateUsage.RPC_API_TLS.publicName,
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
            whenever(certificatesClient.retrieveCertificates(null, CertificateUsage.RPC_API_TLS, "alias")).doReturn(null)
            assertThrows<ResourceNotFoundException> {
                certificatesOps.getCertificateChain(
                    CertificateUsage.RPC_API_TLS.publicName,
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
    }
}
