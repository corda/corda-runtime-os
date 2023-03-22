package net.corda.crypto.softhsm.impl

import java.time.Instant
import java.util.Random
import javax.persistence.EntityManager
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.softhsm.SigningRepository
import net.corda.v5.crypto.DigestAlgorithmName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertNotNull

class SigningRepositoryTest {

    private val random = Random(0)
    private val hash = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    private val signingKey = SigningKeyEntity(
        "tenant",
        "0123456789AB",
        hash.toString(),
        Instant.ofEpochMilli(1),
        "category",
        "schemeCodeName",
        "1".toByteArray(),
        "2".toByteArray(),
        0,
        "masterKeyAlias",
        "alias",
        "hsmAlias",
        "externalId",
        "hsmId",
        SigningKeyEntityStatus.NORMAL,
    )


    @ParameterizedTest
    @ValueSource(classes = [SigningPublicKeySaveContext::class, SigningWrappedKeySaveContext::class])
    fun `repository can save a signing key`(contextType: Class<*>) {
        var stored: SigningKeyEntity? = null
        val secureHash = SecureHashImpl("", "1".toByteArray())
        val em = mock<EntityManager> {
            on { transaction } doReturn mock()
            on { persist(any()) } doAnswer {
                stored = it.getArgument(0)
            }
        }
        val repo = SigningRepositoryImpl(
            mock { on { createEntityManager() } doReturn em },
            "a",
            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
            mock { on { hash(any<ByteArray>(), any()) } doReturn secureHash },
            mock(),
        )

        val context = if (contextType == SigningPublicKeySaveContext::class.java) {
            mock<SigningPublicKeySaveContext> {
                val generatedKey = mock<GeneratedPublicKey> { on { publicKey } doReturn mock() }
                on { key } doReturn generatedKey
                on { alias } doReturn "alias"
                on { category } doReturn "category"
                on { keyScheme } doReturn RSA_TEMPLATE.makeScheme("R3")
                on { externalId } doReturn "externalId"
                on { hsmId } doReturn "hsmId"
            }
        } else {
            mock<SigningWrappedKeySaveContext> {
                val generatedKey = mock<GeneratedWrappedKey> { on { publicKey } doReturn mock() }
                on { key } doReturn generatedKey
                on { alias } doReturn "alias"
                on { category } doReturn "category"
                on { keyScheme } doReturn RSA_TEMPLATE.makeScheme("R3")
                on { externalId } doReturn "externalId"
                on { hsmId } doReturn "hsmId"
            }
        }

        when (context) {
            is SigningPublicKeySaveContext -> repo.savePublicKey(context)
            is SigningWrappedKeySaveContext -> repo.savePrivateKey(context)
        }
        
        verify(em).persist(any())
        assertThat(stored!!.tenantId).isEqualTo("a")
        assertThat(stored!!.fullKeyId).isEqualTo(secureHash.toString())
    }

    @Test
    fun `repository can find a saved signing key by alias`() {
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(SigningKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()) } doReturn it
                    on { setParameter(ArgumentMatchers.anyString(), ArgumentMatchers.anyCollection<String>()) } doReturn it
                    on { resultList } doReturn listOf(signingKey)
                }
            }
        }

        val repo = SigningRepositoryImpl(
            mock { on { createEntityManager() } doReturn em },
            "123",
            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
            mock { on { hash(any<ByteArray>(), any()) } doReturn hash },
            mock(),
        )

        val ret = repo.findKey("alias")
        assertNotNull(ret)
        assertThat(ret).usingRecursiveComparison().isEqualTo(signingKey.toSigningKeyInfo())
    }

    @Test
    fun `repository can correctly looks up a signing key by short ids`() {
        val hashA = ShortHash.of("0123456789AB")
        val hashB = ShortHash.of("123456789ABC")
        val keys = setOf(hashA, hashB)
        val tenantCap = argumentCaptor<String>()
        val keyIdsCap = argumentCaptor<List<String>>()
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(SigningKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(eq("tenantId"), tenantCap.capture()) } doReturn it
                    on { setParameter(eq("keyIds"), keyIdsCap.capture()) } doReturn it
                    on { resultList } doReturn listOf(signingKey)
                }
            }
        }

        val repo = SigningRepositoryImpl(
            mock { on { createEntityManager() } doReturn em },
            "t",
            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
            mock { on { hash(any<ByteArray>(), any()) } doReturn hash },
            mock(),
        )

        repo.lookupByPublicKeyShortHashes(keys)
    }

    @Test
    fun `repository correctly looks up a signing key by full ids`() {
        val hashA = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "0123456789AB".toByteArray())
        val hashB = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "123456789ABC".toByteArray())
        val keys = setOf(hashA, hashB)
        val fullIdsCap = argumentCaptor<List<String>>()
        val tenantCap = argumentCaptor<String>()

        val em = mock<EntityManager> {
            on { createQuery(any(), eq(SigningKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(eq("tenantId"), tenantCap.capture()) } doReturn it
                    on { setParameter(eq("fullKeyIds"), fullIdsCap.capture()) } doReturn it
                    on { resultList } doReturn listOf(signingKey)
                }
            }
        }

        val repo = SigningRepositoryImpl(
            mock { on { createEntityManager() } doReturn em },
            "tenant",
            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
            mock { on { hash(any<ByteArray>(), any()) } doReturn hash },
            mock(),
        )

        repo.lookupByPublicKeyHashes(keys)

        assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
        assertThat(fullIdsCap.allValues.single()).isEqualTo(listOf(hashA.toString(), hashB.toString()))
    }


}