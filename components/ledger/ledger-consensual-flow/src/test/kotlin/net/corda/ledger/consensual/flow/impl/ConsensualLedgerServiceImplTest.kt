package net.corda.ledger.consensual.flow.impl

import net.corda.ledger.common.test.LedgerTest
import net.corda.ledger.common.testkit.mockSigningService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactoryImpl
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertIs

class ConsensualLedgerServiceImplTest: LedgerTest() {
    private val consensualSignedTransactionFactory = ConsensualSignedTransactionFactoryImpl(
        serializationServiceNullCfg,
        mockSigningService(),
        mock(),
        transactionMetadataFactory,
        wireTransactionFactory,
        flowFiberService,
        jsonMarshallingService
    )

    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val service = ConsensualLedgerServiceImpl(consensualSignedTransactionFactory, flowEngine)
        val transactionBuilder = service.getTransactionBuilder()
        assertIs<ConsensualTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `ConsensualLedgerServiceImpl's getTransactionBuilder() can build a SignedTransaction`() {
        val service = ConsensualLedgerServiceImpl(consensualSignedTransactionFactory, flowEngine)
        val transactionBuilder = service.getTransactionBuilder()
        val signedTransaction = transactionBuilder
            .withStates(consensualStateExample)
            .sign(publicKeyExample)
        assertIs<ConsensualSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)
    }
}
