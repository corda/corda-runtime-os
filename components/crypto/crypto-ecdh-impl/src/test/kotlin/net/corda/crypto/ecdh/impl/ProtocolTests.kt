package net.corda.crypto.ecdh.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.ecdh.ECDH_KEY_AGREEMENT_ALGORITHM
import net.corda.crypto.ecdh.EphemeralKeyPairProvider
import net.corda.crypto.ecdh.StableKeyPairProvider
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.v5.base.util.toHex
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.sha256Bytes
import org.bouncycastle.jcajce.provider.util.DigestFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.security.KeyPair
import java.util.UUID
import javax.crypto.KeyAgreement


class ProtocolTests {
    private val digestName = "SHA-256"
    private lateinit var tenantId: String
    private lateinit var coordinatorFactory: TestLifecycleCoordinatorFactoryImpl
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var cryptoOpsClient: CryptoOpsClient
    private lateinit var ephemeralProvider: EphemeralKeyPairProvider
    private lateinit var stableProvider: StableKeyPairProvider
    private lateinit var mgmStableKeyPair: KeyPair

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString().toByteArray().sha256Bytes().toHex().take(12)
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        schemeMetadata = CipherSchemeMetadataImpl()
        mgmStableKeyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        cryptoOpsClient = mock {
            on { deriveSharedSecret(any(), any(), any(), any()) } doAnswer {
                KeyAgreement.getInstance(
                    ECDH_KEY_AGREEMENT_ALGORITHM,
                    schemeMetadata.providers.getValue(schemeMetadata.findKeyScheme(mgmStableKeyPair.public).providerName)
                ).apply {
                    init(mgmStableKeyPair.private)
                    doPhase(it.getArgument(2), true)
                }.generateSecret()
            }
        }
        ephemeralProvider = EphemeralKeyPairProviderImpl(schemeMetadata)
        stableProvider = StableKeyPairProviderImpl(coordinatorFactory, cryptoOpsClient)
    }

    private fun generateSalt() = ByteArray(DigestFactory.getDigest("SHA-256").digestSize).apply {
        schemeMetadata.secureRandom.nextBytes(this)
    }

    @Test
    fun `Should run through handshake using same shared key to send and receive`() {
        val salt = generateSalt()
        val info = "Hello World!".toByteArray()

        val member = ephemeralProvider.create(mgmStableKeyPair.public, digestName)
        val mgm = stableProvider.create(tenantId, mgmStableKeyPair.public, member.publicKey, digestName)

        val plainTextA = "Hello MGM!".toByteArray()
        val cipherTextA = member.encrypt(salt, info, plainTextA)
        val decryptedPlainTexA = mgm.decrypt(salt, info, cipherTextA)
        assertArrayEquals(plainTextA, decryptedPlainTexA)

        val plainTextB = "Hello member!".toByteArray()
        val cipherTextB = mgm.encrypt(salt, info, plainTextB)
        val decryptedPlainTexB = member.decrypt(salt, info, cipherTextB)
        assertArrayEquals(plainTextB, decryptedPlainTexB)
    }
}