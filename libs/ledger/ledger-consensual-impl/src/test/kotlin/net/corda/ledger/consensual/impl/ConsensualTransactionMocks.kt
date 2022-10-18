package net.corda.ledger.consensual.impl

import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.internal.transaction.CordaPackageSummary
import net.corda.ledger.consensual.impl.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.impl.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
import net.corda.ledger.consensual.impl.transaction.factory.getCpiSummary
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.PublicKey

class TestConsensualState(
    val testField: String,
    override val participants: List<PublicKey>
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

        val testPublicKey: PublicKey = kpg.genKeyPair().public
        val testConsensualState = TestConsensualState("test", listOf(testPublicKey))

        fun mockTransactionMetaData() =
            TransactionMetaData(
                linkedMapOf(
                    TransactionMetaData.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
                    TransactionMetaData.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
                    TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
                    TransactionMetaData.PLATFORM_VERSION_KEY to 123,
                    TransactionMetaData.CPI_METADATA_KEY to getCpiSummary(),
                    TransactionMetaData.CPK_METADATA_KEY to listOf(
                        CordaPackageSummary(
                            "MockCpk",
                            "1",
                            "",
                            "0101010101010101010101010101010101010101010101010101010101010101"),
                        CordaPackageSummary(
                            "MockCpk",
                            "3",
                            "",
                            "0303030303030303030303030303030303030303030303030303030303030303")
                    )
                )
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
