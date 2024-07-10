package net.corda.ledger.lib

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.lib.common.Constants.TENANT_ID
import net.corda.ledger.lib.impl.PersistenceServiceImpl
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.digestService
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.merkleProofFactory
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.merkleTreeProvider
import net.corda.ledger.lib.dependencies.db.DbDependencies.cryptoEntityManagerFactory
import net.corda.ledger.lib.dependencies.db.DbDependencies.cryptoEntityManagerFactory2
import net.corda.ledger.lib.dependencies.db.DbDependencies.ledgerEntityManagerFactory
import net.corda.ledger.lib.dependencies.json.JsonDependencies.jsonMarshallingService
import net.corda.ledger.lib.dependencies.json.JsonDependencies.jsonValidator
import net.corda.ledger.lib.dependencies.ledger.LedgerDependencies.filteredTransactionFactory
import net.corda.ledger.lib.dependencies.ledger.LedgerDependencies.utxoLedgerTransactionFactory
import net.corda.ledger.lib.dependencies.ledger.LedgerDependencies.utxoRepo
import net.corda.ledger.lib.dependencies.ledger.LedgerDependencies.utxoSignedTransactionFactoryImpl
import net.corda.ledger.lib.dependencies.serialization.SerializationDependencies.serializationService
import net.corda.ledger.lib.dependencies.signing.SigningDependencies.signingRepoFactory
import net.corda.ledger.persistence.json.impl.ContractStateVaultJsonFactoryRegistryImpl
import net.corda.ledger.persistence.json.impl.DefaultContractStateVaultJsonFactoryImpl
import net.corda.ledger.persistence.utxo.impl.UtxoPersistenceServiceImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.orm.utils.transaction
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.membership.NotaryInfo
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.*

fun main() {
    val utxoPersistenceService = UtxoPersistenceServiceImpl(
        ledgerEntityManagerFactory,
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
        utxoLedgerTransactionFactory,
        ledgerEntityManagerFactory
    )
    // Get a key
    val key = signingRepoFactory.getInstance(TENANT_ID).findKey("30E895BAE560-LEDGER")!!

    val txBuilder = UtxoTransactionBuilderImpl(utxoSignedTransactionFactoryImpl, StubNotaryLookup())
        .setNotary(MemberX500Name("Alice", "Alice Corp", "LDN", "GB"))
        .setTimeWindowUntil(Instant.now().plusSeconds(360000))
        .addSignatories(key.publicKey)
        .addOutputState(UtxoStateClassExample("blabla", listOf(key.publicKey)))
        .addCommand(UtxoCommandExample("bla"))

    val tx = txBuilder.toSignedTransaction()

    persistenceService.persist(tx, TransactionStatus.VERIFIED)

    println(tx)
}

private class StubNotaryLookup : NotaryLookup {
    override fun getNotaryServices(): MutableCollection<NotaryInfo> {
        return mutableListOf()
    }

    override fun lookup(notaryServiceName: MemberX500Name): NotaryInfo? {
        return object : NotaryInfo {
            override fun getName() = notaryServiceName
            override fun getProtocol() = "whatever"
            override fun getProtocolVersions() = mutableListOf(1)
            override fun getPublicKey() = keyPairExample.public
            override fun isBackchainRequired() = false
        }
    }

    override fun isNotaryVirtualNode(virtualNodeName: MemberX500Name): Boolean {
        return true
    }
}

private val kpg = KeyPairGenerator.getInstance("EC")
    .apply { initialize(ECGenParameterSpec("secp256r1")) }

val keyPairExample: KeyPair = kpg.generateKeyPair()

@BelongsToContract(UtxoContractExample::class)
class UtxoStateClassExample(val testField: String, private val participants: List<PublicKey>) : ContractState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    override fun equals(other: Any?): Boolean {
        return this === other
                || other is UtxoStateClassExample
                && other.testField == testField
                && other.participants == participants
    }

    override fun hashCode(): Int = Objects.hash(testField, participants)
}

data class UtxoCommandExample(val commandArgument: String? = null) : Command {
    override fun equals(other: Any?): Boolean =
        (this === other) || (
                other is UtxoCommandExample &&
                        commandArgument == other.commandArgument
                )

    override fun hashCode(): Int = commandArgument.hashCode()
}

class UtxoContractExample : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
    }
}
