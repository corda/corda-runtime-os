package net.corda.uniqueness.client.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEYS
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LedgerUniquenessCheckerClientServiceImplTest {

    private companion object {
        val aliceNotaryVNodeKey = mock<PublicKey>()
        val bobNotaryVNodeKey = mock<PublicKey>()
        val charlieNotaryVNodeKey = mock<PublicKey>()

        val dummyTxId = SecureHashUtils.randomSecureHash()

        val notaryServiceCompositeKey = mock<CompositeKey> {
            on { leafKeys } doReturn setOf(aliceNotaryVNodeKey, bobNotaryVNodeKey, charlieNotaryVNodeKey)
        }
    }

    private val argumentCaptor = argumentCaptor<Class<out UniquenessCheckExternalEventFactory>>()

    @Test
    fun `Signing is successful when the provided notary service composite key contains the current vnode key`() {
        val response = createClientService(
            currentVNodeNotaryKey = aliceNotaryVNodeKey
        ).requestUniquenessCheck(
            dummyTxId.toString(),
            emptyList(),
            emptyList(),
            5,
            null,
            Instant.now(),
            notaryServiceCompositeKey
        )

        assertThat(response.result).isInstanceOf(UniquenessCheckResultSuccess::class.java)
        assertThat(response.signature).isNotNull
        assertThat(response.signature?.by).isEqualTo(aliceNotaryVNodeKey)
    }

    @Test
    fun `Signing is successful when the provided notary service key is a simple key and the selected key matches`() {
        val response = createClientService(
            currentVNodeNotaryKey = aliceNotaryVNodeKey
        ).requestUniquenessCheck(
            dummyTxId.toString(),
            emptyList(),
            emptyList(),
            5,
            null,
            Instant.now(),
            // The notary service consists of a single VNode who is Alice
            aliceNotaryVNodeKey
        )

        assertThat(response.result).isInstanceOf(UniquenessCheckResultSuccess::class.java)
        assertThat(response.signature).isNotNull
        assertThat(response.signature?.by).isEqualTo(aliceNotaryVNodeKey)
    }

    @Test
    fun `Signing is unsuccessful when the provided notary service key is a simple key and the selected key does not match`() {
        val exception = assertThrows<IllegalArgumentException> {
            createClientService(
                currentVNodeNotaryKey = aliceNotaryVNodeKey
            ).requestUniquenessCheck(
                dummyTxId.toString(),
                emptyList(),
                emptyList(),
                5,
                null,
                Instant.now(),
                // The notary service consists of a single VNode who is Bob
                bobNotaryVNodeKey
            )
        }

        assertThat(exception).hasStackTraceContaining(
            "The notary key selected for signing is not associated with the notary service key."
        )
    }

    @Test
    fun `Signing is successful when the provided notary service composite key contains the current vnode key (not as the first element)`() {
        val response = createClientService(
            currentVNodeNotaryKey = bobNotaryVNodeKey
        ).requestUniquenessCheck(
            dummyTxId.toString(),
            emptyList(),
            emptyList(),
            5,
            null,
            Instant.now(),
            notaryServiceCompositeKey
        )

        assertThat(response.result).isInstanceOf(UniquenessCheckResultSuccess::class.java)
        assertThat(response.signature).isNotNull
        assertThat(response.signature?.by).isEqualTo(bobNotaryVNodeKey)
    }

    @Test
    fun `Uniqueness check client responds with failure if uniqueness check has failed`() {
        val response = createClientService(
            currentVNodeNotaryKey = aliceNotaryVNodeKey,
            uniquenessCheckResult = UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorMalformedRequestImpl("Malformed")
            )
        ).requestUniquenessCheck(
            dummyTxId.toString(),
            emptyList(),
            emptyList(),
            5,
            null,
            Instant.now(),
            notaryServiceCompositeKey
        )

        assertThat(response.result).isInstanceOf(UniquenessCheckResultFailure::class.java)
        assertThat((response.result as UniquenessCheckResultFailure).error).isInstanceOf(
            UniquenessCheckErrorMalformedRequest::class.java
        )
        assertThat(response.signature).isNull()
    }

    @Test
    fun `Signing is unsuccessful when the provided notary service composite key does not contain the current vnode key`() {
        val exception = assertThrows<IllegalArgumentException> {
            // Provide a random key that is not part of the composite key, this way signing should fail
            createClientService(currentVNodeNotaryKey = mock()).requestUniquenessCheck(
                dummyTxId.toString(),
                emptyList(),
                emptyList(),
                5,
                null,
                Instant.now(),
                notaryServiceCompositeKey
            )
        }

        assertThat(exception).hasStackTraceContaining(
            "The notary key selected for signing is not associated with the notary service key."
        )
    }

    @Test
    fun `Signing is unsuccessful when the current vnode has no notary keys (is not a notary vnode)`() {
        val exception = assertThrows<IllegalArgumentException> {
            createClientService(currentVNodeNotaryKey = null).requestUniquenessCheck(
                dummyTxId.toString(),
                emptyList(),
                emptyList(),
                5,
                null,
                Instant.now(),
                notaryServiceCompositeKey
            )
        }

        assertThat(exception).hasStackTraceContaining(
            "Could not find any keys associated with the notary service's public key."
        )
    }

    private fun createClientService(
        currentVNodeNotaryKey: PublicKey?,
        uniquenessCheckResult: UniquenessCheckResult? = UniquenessCheckResultSuccessImpl(Instant.now()),
    ): LedgerUniquenessCheckerClientService {
        val mockExternalEventExecutor = mock<ExternalEventExecutor>()
        whenever(mockExternalEventExecutor.execute(argumentCaptor.capture(), any()))
            .thenReturn(uniquenessCheckResult)

        val mockMerkleTree = mock<MerkleTree> {
            on { root } doReturn dummyTxId
        }
        val mockMerkleTreeFactory = mock<MerkleTreeFactory> {
            on { createHashDigest(any(), any()) } doReturn mock()
            on { createTree(any(), any()) } doReturn mockMerkleTree
        }

        val dummySignature = DigitalSignature.WithKey(
            // If the provided key is null, this will not be returned anyway so we just mock it
            currentVNodeNotaryKey ?: mock(),
            "some bytes".toByteArray(),
            mapOf()
        )

        val mockSigningService = mock<SigningService> {
            on { sign(any(), any(), any()) } doReturn dummySignature
            on { findMySigningKeys(any()) } doReturn mapOf(mock<PublicKey>() to currentVNodeNotaryKey)
        }

        val mockDigestService = mock<DigestService> {
            on { hash(any<ByteArray>(), any()) } doReturn SecureHashUtils.randomSecureHash()
        }

        // If currentVNodeNotaryKey is null, return empty list. This way we can simulate a non-notary VNode
        // (i.e. no notary keys)
        val mockMemberContext = mock<MemberContext> {
            on { parseList(eq(NOTARY_KEYS), eq(PublicKey::class.java)) } doReturn (currentVNodeNotaryKey?.let {
                listOf(it)
            } ?: emptyList())
        }

        val myMockInfo = mock<MemberInfo> {
            on { memberProvidedContext } doReturn mockMemberContext
            on { platformVersion } doReturn 1
        }
        val mockMemberLookup = mock<MemberLookup> {
            on { myInfo() } doReturn myMockInfo
        }

        return LedgerUniquenessCheckerClientServiceImpl(
            mockExternalEventExecutor,
            mockDigestService,
            mockSigningService,
            mockMerkleTreeFactory,
            mockMemberLookup
        )
    }
}