package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.httprpc.v1.types.response.KeyMetaData
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_SHA256_TEMPLATE
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.publicKeyId
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_emailProtection
import org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_serverAuth
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom

class KeysRpcOpsImplTest {
    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private val keyEncodingService = mock<KeyEncodingService>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val cipherSchemeMetadata = mock<CipherSchemeMetadata>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val keysOps = KeysRpcOpsImpl(cryptoOpsClient, keyEncodingService, lifecycleCoordinatorFactory, cipherSchemeMetadata)

    @Nested
    inner class BasicApiTests {
        @Test
        fun `listKeys return the correct key IDs`() {
            val keys = (1..4).map {
                val idToReturn = "id.$it"
                val aliasToReturn = "alias-$it"
                val categoryToReturn = "category:$it"
                val scheme = "scheme($it)"
                mock<CryptoSigningKey> {
                    on { id } doReturn idToReturn
                    on { alias } doReturn aliasToReturn
                    on { category } doReturn categoryToReturn
                    on { schemeCodeName } doReturn scheme
                }
            }
            whenever(cryptoOpsClient.lookup("id", 0, 500, CryptoKeyOrderBy.NONE, emptyMap())).doReturn(keys)

            val list = keysOps.listKeys("id")

            assertThat(list)
                .containsEntry(
                    "id.1", KeyMetaData("id.1", "alias-1", "category:1", "scheme(1)")
                )
                .containsEntry(
                    "id.2", KeyMetaData("id.2", "alias-2", "category:2", "scheme(2)")
                )
                .containsEntry(
                    "id.3", KeyMetaData("id.3", "alias-3", "category:3", "scheme(3)")
                )
                .containsEntry(
                    "id.4", KeyMetaData("id.4", "alias-4", "category:4", "scheme(4)")
                )
        }

        @Test
        fun `generateKeyPair returns the generated public key ID`() {
            val publicKey = mock<PublicKey> {
                on { encoded } doReturn byteArrayOf(1, 2, 3)
            }
            whenever(cryptoOpsClient.generateKeyPair("tenantId", "category", "alias", "scheme")).doReturn(publicKey)

            val id = keysOps.generateKeyPair(tenantId = "tenantId", alias = "alias", hsmCategory = "category", scheme = "scheme")

            assertThat(id).isEqualTo(publicKey.publicKeyId())
        }

        @Test
        fun `generateKeyPem returns the keys PEMs`() {
            val keyId = "keyId"
            val holdingIdentityId = "holdingIdentityId"
            val publicKeyBytes = "123".toByteArray()
            val key = mock<CryptoSigningKey> {
                on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytes)
            }
            val decodedPublicKey = mock<PublicKey>()
            whenever(cryptoOpsClient.lookup(holdingIdentityId, listOf(keyId))).doReturn(listOf(key))
            whenever(keyEncodingService.decodePublicKey(publicKeyBytes)).doReturn(decodedPublicKey)
            whenever(keyEncodingService.encodeAsString(decodedPublicKey)).doReturn("PEM")

            val pem = keysOps.generateKeyPem(holdingIdentityId, keyId)

            assertThat(pem).isEqualTo("PEM")
        }

        @Test
        fun `generateKeyPem throws Exception when the key is unknwon`() {
            val keyId = "keyId"
            val holdingIdentityId = "holdingIdentityId"
            whenever(cryptoOpsClient.lookup(holdingIdentityId, listOf(keyId))).doReturn(emptyList())

            assertThrows<ResourceNotFoundException> {
                keysOps.generateKeyPem(holdingIdentityId, keyId)
            }
        }

        @Test
        fun `listSchemes return list of schemes`() {
            whenever(cryptoOpsClient.getSupportedSchemes("id", "category")).doReturn(listOf("one", "two"))

            val schemes = keysOps.listSchemes("id", "category")

            assertThat(schemes).containsExactlyInAnyOrder("one", "two")
        }
    }

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `isRunning returns the coordinator status`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            assertThat(keysOps.isRunning).isTrue
        }

        @Test
        fun `start starts the coordinator`() {
            keysOps.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop stops the coordinator`() {
            keysOps.stop()

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
        private val holdingIdentityId = "id"
        private val keyId = "keyId"
        private val x500Name = "CN=Alice"
        private val email = "name@r3.com"
        private val publicKeyBytes = "123".toByteArray()
        private val schema = ECDSA_SECP256K1_SHA256_TEMPLATE.makeScheme("Test")
        private val key = mock<CryptoSigningKey> {
            on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytes)
            on { schemeCodeName } doReturn schema.codeName
            on { tenantId } doReturn holdingIdentityId
        }
        private val publicKey = KeyPairGenerator.getInstance("EC").let { keyPairGenerator ->
            val rnd = mock<SecureRandom> {
                on { nextBytes(any()) } doAnswer {
                    val array = it.arguments[0] as ByteArray
                    array.fill(106)
                }
            }
            keyPairGenerator.initialize(571, rnd)
            keyPairGenerator.generateKeyPair().public
        }

        @BeforeEach
        fun setUp() {
            whenever(cryptoOpsClient.lookup(holdingIdentityId, listOf(keyId))).doReturn(listOf(key))
            whenever(
                cryptoOpsClient.sign(
                    eq(holdingIdentityId), eq(publicKey), any(), eq(emptyMap())
                )
            ).doReturn(
                DigitalSignature.WithKey(
                    publicKey,
                    byteArrayOf(1)
                )
            )
            whenever(keyEncodingService.decodePublicKey(publicKeyBytes)).doReturn(publicKey)
            whenever(cipherSchemeMetadata.schemes).doReturn(arrayOf(schema))
        }
        @Test
        fun `it throws exception if key is not available`() {
            whenever(cryptoOpsClient.lookup(any(), any())).doReturn(emptyList())

            assertThrows<ResourceNotFoundException> {
                keysOps.generateCsr(
                    holdingIdentityId,
                    keyId,
                    x500Name,
                    email,
                    null,
                    null
                )
            }
        }

        @Test
        fun `it sign the request`() {
            keysOps.generateCsr(
                holdingIdentityId,
                keyId,
                x500Name,
                email,
                null,
                null
            )

            verify(cryptoOpsClient).sign(eq(holdingIdentityId), eq(publicKey), any(), eq(emptyMap()))
        }

        @Test
        fun `it returns the correct signature`() {
            val pem = keysOps.generateCsr(
                holdingIdentityId,
                keyId,
                x500Name,
                email,
                null,
                null
            )

            assertThat(pem.fromPem().signature).isEqualTo(byteArrayOf(1))
        }

        @Test
        fun `it adds server auth as extended usage if non had been specify`() {
            val pem = keysOps.generateCsr(
                holdingIdentityId,
                keyId,
                x500Name,
                email,
                null,
                null
            )

            assertThat(
                pem.fromPem()
                    .requestedExtensions
                    .getExtension(Extension.extendedKeyUsage)
            ).isEqualTo(
                Extension(
                    Extension.extendedKeyUsage,
                    true,
                    DEROctetString(
                        ExtendedKeyUsage(
                            arrayOf(
                                id_kp_serverAuth
                            )
                        )
                    )
                )
            )
        }

        @Test
        fun `it adds explicit purpose when one is provided`() {
            val pem = keysOps.generateCsr(
                holdingIdentityId,
                keyId,
                x500Name,
                email,
                "1.3.6.1.5.5.7.3.4",
                null
            )

            assertThat(
                pem.fromPem()
                    .requestedExtensions
                    .getExtension(Extension.extendedKeyUsage)
            ).isEqualTo(
                Extension(
                    Extension.extendedKeyUsage,
                    true,
                    DEROctetString(
                        ExtendedKeyUsage(
                            arrayOf(
                                id_kp_emailProtection
                            )
                        )
                    )
                )
            )
        }

        @Test
        fun `it adds alternative subject names when some are provided`() {
            val pem = keysOps.generateCsr(
                holdingIdentityId,
                keyId,
                x500Name,
                email,
                null,
                listOf("www.alice.net", "alice.net")
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
                            )
                        )
                    )
                )
            )
        }

        @Test
        fun `it will not adds alternative subject names when none are provided`() {
            val pem = keysOps.generateCsr(
                holdingIdentityId,
                keyId,
                x500Name,
                email,
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
            val pem = keysOps.generateCsr(
                holdingIdentityId,
                keyId,
                x500Name,
                email,
                null,
                null,
            )

            assertThat(
                pem.fromPem()
                    .subject
            ).isEqualTo(X500Name(x500Name))
        }

        @Test
        fun `it will use the correct email address`() {
            val pem = keysOps.generateCsr(
                holdingIdentityId,
                keyId,
                x500Name,
                email,
                null,
                null,
            )

            assertThat(
                pem.fromPem()
                    .getAttributes(PKCSObjectIdentifiers.pkcs_9_at_emailAddress).flatMap { it.attributeValues.toList() }
            ).hasSize(1)
                .contains(DERUTF8String(email))
        }

        @Test
        fun `it will throw an exception for invalid schema name`() {
            whenever(cipherSchemeMetadata.schemes).doReturn(emptyArray())

            assertThrows<ResourceNotFoundException> {
                keysOps.generateCsr(
                    holdingIdentityId,
                    keyId,
                    x500Name,
                    email,
                    null,
                    null,
                )
            }
        }

        @Test
        fun `it will throw an exception for schema without algorithm identifier`() {
            val schema = COMPOSITE_KEY_TEMPLATE.makeScheme("TEST")
            whenever(cipherSchemeMetadata.schemes).doReturn(arrayOf(schema))
            whenever(key.schemeCodeName).doReturn(schema.codeName)

            assertThrows<ResourceNotFoundException> {
                keysOps.generateCsr(
                    holdingIdentityId,
                    keyId,
                    x500Name,
                    email,
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
}
