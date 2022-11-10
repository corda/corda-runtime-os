package net.corda.ledger.utxo.test

import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.mockSigningService
import net.corda.ledger.utxo.flow.impl.UtxoLedgerServiceImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactoryImpl
import org.mockito.kotlin.mock

abstract class UtxoLedgerTest : CommonLedgerTest() {
    val utxoSignedTransactionFactory = UtxoSignedTransactionFactoryImpl(
        serializationServiceNullCfg,
        mockSigningService(),
        mock(),
        transactionMetadataFactory,
        wireTransactionFactory,
        flowFiberService,
        jsonMarshallingService
    )
    val utxoLedgerService = UtxoLedgerServiceImpl(utxoSignedTransactionFactory)

    // This is the only not stateless.
    val utxoTransactionBuilder = UtxoTransactionBuilderImpl(utxoSignedTransactionFactory)
}
