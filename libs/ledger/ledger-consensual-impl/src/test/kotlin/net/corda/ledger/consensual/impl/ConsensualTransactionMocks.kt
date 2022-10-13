package net.corda.ledger.consensual.impl

import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.time.Instant
import net.corda.v5.membership.MGMContext
import net.corda.virtualnode.HoldingIdentity
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq

class TestConsensualState(
    val testField: String,
    override val participants: List<Party>
) : ConsensualState {
    override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestConsensualState) return false
        if (other.testField != testField) return false
        if (other.participants.size != participants.size) return false
        return other.participants.containsAll(participants)
    }

    override fun hashCode(): Int = testField.hashCode() + participants.hashCode() * 31
}

class ConsensualTransactionMocks {
    companion object {
        private val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA").also {
            it.initialize(512)
        }

        val testMemberX500Name = MemberX500Name("R3", "London", "GB")
        val testPublicKey = kpg.genKeyPair().public
        val testPartyImpl = Party(testMemberX500Name, testPublicKey)
        val testConsensualState = TestConsensualState("test", listOf(testPartyImpl))

        fun mockSandboxCpks(): List<CpkMetadata> {
            return listOf(
                makeCpkMetadata(1, CordappType.CONTRACT),
                makeCpkMetadata(2, CordappType.WORKFLOW),
                makeCpkMetadata(3, CordappType.CONTRACT),
            )
        }

        private fun makeCpkMetadata(i: Int, cordappType: CordappType) = CpkMetadata(
            CpkIdentifier("MockCpk", "$i", null),
            CpkManifest(CpkFormatVersion(1, 1)),
            "mock-bundle-$i",
            emptyList(),
            emptyList(),
            CordappManifest(
                "mock-bundle-symbolic",
                "$i",
                1,
                1,
                cordappType,
                "mock-shortname",
                "r3",
                i,
                "None",
                emptyMap()
            ),
            CpkType.UNKNOWN,
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32) { i.toByte() }),
            emptySet(),
            Instant.now()
        )

        fun mockSigningService(): SigningService {
            val signingService: SigningService = mock()
            val signature = DigitalSignature.WithKey(testPublicKey, "0".toByteArray(), mapOf())
            whenever(signingService.sign(any(), any(), any())).thenReturn(signature)
            return signingService
        }

        fun mockPlatformInfoProvider(): PlatformInfoProvider{
            val platformInfoProvider: PlatformInfoProvider = mock()
            whenever(platformInfoProvider.activePlatformVersion).thenReturn(123)
            return platformInfoProvider
        }
    }
}
