package net.corda.crypto.persistence.db.model

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.publicKeyIdOf
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class SigningKeyEntityTests {
    private class MockPublicKey(
        private val encoded: ByteArray
    ) : PublicKey {
        override fun getAlgorithm(): String = "MOCK"
        override fun getFormat(): String = "MOCK"
        override fun getEncoded(): ByteArray = encoded
    }

    @Test
    fun `Should equal when tenantId and keyId properties are matching`() {
        val random = Random(Instant.now().toEpochMilli())
        val publicKey = MockPublicKey(random.nextBytes(512))
        val tenantId = publicKeyIdOf(UUID.randomUUID().toString().toByteArray())
        val keyId = publicKeyIdOf(publicKey)
        val e1 = SigningKeyEntity(
            tenantId = tenantId,
            keyId = keyId,
            created = Instant.now().minusSeconds(5),
            category = CryptoConsts.HsmCategories.LEDGER,
            schemeCodeName = RSA_CODE_NAME,
            publicKey = publicKey.encoded,
            keyMaterial = random.nextBytes(512),
            encodingVersion = 1,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
        )
        val e2 = SigningKeyEntity(
            tenantId = tenantId,
            keyId = keyId,
            created = Instant.now().minusSeconds(50),
            category = CryptoConsts.HsmCategories.TLS,
            schemeCodeName = EDDSA_ED25519_CODE_NAME,
            publicKey = publicKey.encoded,
            keyMaterial = random.nextBytes(512),
            encodingVersion = 11,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
        )
        Assertions.assertEquals(e1, e2)
    }

    @Test
    fun `Should equal to itself`() {
        val random = Random(Instant.now().toEpochMilli())
        val publicKey = MockPublicKey(random.nextBytes(512))
        val tenantId = publicKeyIdOf(UUID.randomUUID().toString().toByteArray())
        val keyId = publicKeyIdOf(publicKey)
        val e1 = SigningKeyEntity(
            tenantId = tenantId,
            keyId = keyId,
            created = Instant.now().minusSeconds(5),
            category = CryptoConsts.HsmCategories.LEDGER,
            schemeCodeName = RSA_CODE_NAME,
            publicKey = publicKey.encoded,
            keyMaterial = random.nextBytes(512),
            encodingVersion = 1,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
        )
        Assertions.assertEquals(e1, e1)
    }

    @Test
    fun `Should not equal when tenantId properties are not matching`() {
        val random = Random(Instant.now().toEpochMilli())
        val publicKey = MockPublicKey(random.nextBytes(512))
        val keyId = publicKeyIdOf(publicKey)
        val e1 = SigningKeyEntity(
            tenantId = publicKeyIdOf(UUID.randomUUID().toString().toByteArray()),
            keyId = keyId,
            created = Instant.now().minusSeconds(5),
            category = CryptoConsts.HsmCategories.LEDGER,
            schemeCodeName = RSA_CODE_NAME,
            publicKey = publicKey.encoded,
            keyMaterial = random.nextBytes(512),
            encodingVersion = 1,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
        )
        val e2 = SigningKeyEntity(
            tenantId = publicKeyIdOf(UUID.randomUUID().toString().toByteArray()),
            keyId = keyId,
            created = Instant.now().minusSeconds(50),
            category = CryptoConsts.HsmCategories.TLS,
            schemeCodeName = EDDSA_ED25519_CODE_NAME,
            publicKey = publicKey.encoded,
            keyMaterial = random.nextBytes(512),
            encodingVersion = 11,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
        )

        Assertions.assertNotEquals(e1, e2)
    }

    @Test
    fun `Should not equal to null`() {
        val random = Random(Instant.now().toEpochMilli())
        val publicKey = MockPublicKey(random.nextBytes(512))
        val keyId = publicKeyIdOf(publicKey)
        val e1 = SigningKeyEntity(
            tenantId = publicKeyIdOf(UUID.randomUUID().toString().toByteArray()),
            keyId = keyId,
            created = Instant.now().minusSeconds(5),
            category = CryptoConsts.HsmCategories.LEDGER,
            schemeCodeName = RSA_CODE_NAME,
            publicKey = publicKey.encoded,
            keyMaterial = random.nextBytes(512),
            encodingVersion = 1,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
        )
        Assertions.assertNotEquals(e1, null)
    }
}