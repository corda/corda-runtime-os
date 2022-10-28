package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.orm.utils.transaction
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.utilities.time.UTCClock
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName

class UtxoPersistenceServiceImpl constructor(
    private val sandbox: SandboxGroupContext,
    private val repository: UtxoRepository,
    private val sandboxDigestService: DigestService,
    private val utcClock: UTCClock
) : UtxoPersistenceService {

    override fun persistTransaction(transaction: UtxoTransactionReader) {

        val entityManger = sandbox.getEntityManagerFactory().createEntityManager()
        val nowUtc = utcClock.instant()

        entityManger.transaction { em ->
            val transactionIdString = transaction.id.toString()

            // Insert the Transaction
            repository.persistTransaction(
                em,
                transactionIdString,
                transaction.privacySalt.bytes,
                transaction.account,
                nowUtc
            )

            // Insert the Transactions components
            transaction.rawGroupLists.mapIndexed { groupIndex, leaves ->
                leaves.mapIndexed { leafIndex, data ->
                   repository.persistTransactionComponentLeaf(
                       em,
                       transactionIdString,
                       groupIndex,
                       leafIndex,
                       data,
                       sandboxDigestService.hash(data, DigestAlgorithmName.SHA2_256).toString(),
                       nowUtc
                   )
                }
            }

            // Insert the transactions current status
            repository.persistTransactionStatus(
                em,
                transactionIdString,
                transaction.status,
                nowUtc
            )

            // Insert the CPK details liked to this transaction
            // TODOs: The CPK file meta does not exist yet, this will be implemented by
            // https://r3-cev.atlassian.net/browse/CORE-7626
        }
    }
}
