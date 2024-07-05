package net.corda.ledger.lib

import net.corda.crypto.core.parseSecureHash
import net.corda.ledger.lib.impl.PersistenceServiceImpl
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.digestService
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.merkleProofFactory
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.merkleTreeProvider
import net.corda.ledger.lib.dependencies.db.DbDependencies.entityManagerFactory
import net.corda.ledger.lib.dependencies.json.JsonDependencies.jsonMarshallingService
import net.corda.ledger.lib.dependencies.json.JsonDependencies.jsonValidator
import net.corda.ledger.lib.dependencies.ledger.LedgerDependencies.filteredTransactionFactory
import net.corda.ledger.lib.dependencies.ledger.LedgerDependencies.utxoLedgerTransactionFactory
import net.corda.ledger.lib.dependencies.ledger.LedgerDependencies.utxoRepo
import net.corda.ledger.lib.dependencies.ledger.LedgerDependencies.utxoSignedTransactionFactoryImpl
import net.corda.ledger.lib.dependencies.serialization.SerializationDependencies.serializationService
import net.corda.ledger.persistence.json.impl.ContractStateVaultJsonFactoryRegistryImpl
import net.corda.ledger.persistence.json.impl.DefaultContractStateVaultJsonFactoryImpl
import net.corda.ledger.persistence.utxo.impl.UtxoPersistenceServiceImpl
import net.corda.utilities.time.UTCClock

fun main() {
    val utxoPersistenceService = UtxoPersistenceServiceImpl(
        entityManagerFactory,
        utxoRepo,
        serializationService,
        digestService,
        ContractStateVaultJsonFactoryRegistryImpl(),
        DefaultContractStateVaultJsonFactoryImpl(),
        jsonMarshallingService,
        jsonValidator,
        merkleProofFactory,
        merkleTreeProvider,
        filteredTransactionFactory,
        digestService,
        UTCClock()
    )

    val persistenceService = PersistenceServiceImpl(
        utxoPersistenceService,
        utxoSignedTransactionFactoryImpl,
        utxoLedgerTransactionFactory
    )

    // TODO Serialization is not working yet so this call WILL fail

    // Existing tx
    val tx = persistenceService.findSignedLedgerTransaction(parseSecureHash(
        "SHA-256D:F6B1133B03B0D8D8AE4EC79089187E31557D7A4F0D0D27977A68D7F63109A905")
    )

    println(tx)
}
