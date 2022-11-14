package net.corda.utxo.token.sync.services.impl

import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.utxo.token.sync.converters.DbRecordConverter
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.GROUP_INDEX
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.IS_CONSUMED
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.LAST_MODIFIED
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.LEAF_INDEX
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TOKEN_AMOUNT
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TOKEN_ISSUER_HASH
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TOKEN_NOTARY_X500_NAME
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TOKEN_OWNER_HASH
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TOKEN_SYMBOL
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TOKEN_TAG
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TOKEN_TYPE
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TRANSACTION_ID
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.TokenRecord
import net.corda.utxo.token.sync.entities.TokenRefRecord
import net.corda.utxo.token.sync.services.SyncConfiguration
import net.corda.utxo.token.sync.services.UtxoTokenRepository
import net.corda.v5.ledger.utxo.StateRef
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple

class UtxoTokenRepositoryImpl(
    private val dbRecordConverter: DbRecordConverter
) : UtxoTokenRepository {

    override fun getSpentTokensByRef(entityManager: EntityManager, stateRefs: List<StateRef>): List<TokenRecord> {
        // Create the query criteria in the form "('tx_id', leaf_idx), ('tx_id', leaf_idx), ('tx_id', leaf_idx), ..."
        val criteriaList = stateRefs.joinToString(separator = ", ") {
            "('${it.transactionHash.toString()}',${it.index})"
        }

        return entityManager.createNativeQuery(
            """
            WITH  criteria (transaction_id, leaf_idx) AS ( 
                VALUES $criteriaList
            ) 
            SELECT tx.$TRANSACTION_ID, 
                   tx.$LEAF_INDEX,
                   tx.$TOKEN_TYPE, 
                   tx.$TOKEN_ISSUER_HASH, 
                   tx.$TOKEN_NOTARY_X500_NAME, 
                   tx.$TOKEN_SYMBOL,
                   tx.$TOKEN_OWNER_HASH,
                   tx.$TOKEN_TAG,
                   tx.$TOKEN_AMOUNT,
                   tx.$LAST_MODIFIED
            FROM {h-schema}utxo_transaction_output tx
            INNER JOIN criteria 
            ON    tx.transaction_id = criteria.transaction_id 
            AND   tx.leaf_idx = criteria.leaf_idx
            WHERE tx.$IS_CONSUMED = TRUE
            AND   tx.$GROUP_INDEX = ${UtxoComponentGroup.OUTPUTS.ordinal}
            """,
            Tuple::class.java
        )
            .convertTuple { dbRecordConverter.convertTokenRecord(it) }

    }

    override fun getUnspentTokensFromTimestamp(
        entityManager: EntityManager,
        startRecordTimestamp: Instant,
        maxRecordsToReturn: Int
    ): List<TokenRecord> {
        return entityManager.createNativeQuery(
            """
            SELECT TOP ${maxRecordsToReturn}
                   $TRANSACTION_ID, 
                   $LEAF_INDEX,
                   $TOKEN_TYPE, 
                   $TOKEN_ISSUER_HASH, 
                   $TOKEN_NOTARY_X500_NAME, 
                   $TOKEN_SYMBOL,
                   $TOKEN_OWNER_HASH,
                   $TOKEN_TAG,
                   $TOKEN_AMOUNT,
                   $LAST_MODIFIED
            FROM {h-schema}utxo_transaction_output
            WHERE    $IS_CONSUMED = FALSE
            AND      $GROUP_INDEX = ${UtxoComponentGroup.OUTPUTS.ordinal}
            AND      $LAST_MODIFIED>= (:lastModified)
            ORDER BY $LAST_MODIFIED
            """,
            Tuple::class.java
        )
            .setParameter("lastModified", startRecordTimestamp)
            .convertTuple { dbRecordConverter.convertTokenRecord(it) }
    }

    override fun getUnspentTokenRefsFromTimestamp(
        entityManager: EntityManager,
        poolKeyRecord: TokenPoolKeyRecord,
        startRecordTimestamp: Instant,
        maxRecordsToReturn: Int
    ): List<TokenRefRecord> {

        return entityManager.createNativeQuery(
            """
            SELECT  TOP ${maxRecordsToReturn}
                    $TRANSACTION_ID, 
                    $LEAF_INDEX, 
                    $LAST_MODIFIED
            FROM {h-schema}utxo_transaction_output
            WHERE    $IS_CONSUMED = FALSE
            AND      $GROUP_INDEX = ${UtxoComponentGroup.OUTPUTS.ordinal}
            AND      $TOKEN_TYPE = (:tokenType)
            AND      $TOKEN_ISSUER_HASH = (:issuerHash)
            AND      $TOKEN_NOTARY_X500_NAME = (:notaryX500Name)
            AND      $TOKEN_SYMBOL = (:symbol)
            AND      $LAST_MODIFIED >= (:lastModified)
            ORDER BY $LAST_MODIFIED
            """,
            Tuple::class.java
        )
            .setParameter("tokenType", poolKeyRecord.tokenType)
            .setParameter("issuerHash", poolKeyRecord.issuerHash)
            .setParameter("notaryX500Name", poolKeyRecord.notaryX500Name)
            .setParameter("symbol", poolKeyRecord.symbol)
            .setParameter("lastModified", startRecordTimestamp)
            .convertTuple { dbRecordConverter.convertToTokenRef(it) }
    }

    override fun getDistinctTokenPools(entityManager: EntityManager): Set<TokenPoolKeyRecord> {

        return entityManager.createNativeQuery(
            """
            SELECT DISTINCT  $TOKEN_TYPE, 
                             $TOKEN_ISSUER_HASH, 
                             $TOKEN_NOTARY_X500_NAME, 
                             $TOKEN_SYMBOL
            FROM {h-schema}utxo_transaction_output
            WHERE    $IS_CONSUMED = FALSE
            AND      $GROUP_INDEX = ${UtxoComponentGroup.OUTPUTS.ordinal}
            """,
            Tuple::class.java
        ).convertTuple { dbRecordConverter.convertTokenKey(it) }
            .toSet()

    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Query.convertTuple(mappingFunc: (Tuple) -> T): List<T> {
        return (resultList as List<Tuple>).map { mappingFunc(it) }
    }
}
