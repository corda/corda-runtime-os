package net.corda.utxo.token.sync.services.impl

import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.utxo.token.sync.converters.DbRecordConverter
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.GROUP_INDEX
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.CONSUMED
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.CREATED
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
                   tx.$CREATED
            FROM {h-schema}utxo_transaction_output tx
            INNER JOIN criteria 
            ON    tx.transaction_id = criteria.transaction_id 
            AND   tx.leaf_idx = criteria.leaf_idx
            WHERE tx.$CONSUMED = TRUE
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
                   txo.$TRANSACTION_ID, 
                   txo.$LEAF_INDEX,
                   txo.$TOKEN_TYPE, 
                   txo.$TOKEN_ISSUER_HASH, 
                   txo.$TOKEN_NOTARY_X500_NAME, 
                   txo.$TOKEN_SYMBOL,
                   txo.$TOKEN_OWNER_HASH,
                   txo.$TOKEN_TAG,
                   txo.$TOKEN_AMOUNT,
                   txo.$CREATED
            FROM {h-schema}utxo_transaction_output txo
            INNER JOIN {h-schema}utxo_relevant_transaction_state txr
            ON  txo.$TRANSACTION_ID = txr.$TRANSACTION_ID
            AND txo.$LEAF_INDEX = txr.$LEAF_INDEX
            AND txo.$GROUP_INDEX = txr.$GROUP_INDEX
            WHERE    txr.$CONSUMED = FALSE
            AND      txr.$GROUP_INDEX = ${UtxoComponentGroup.OUTPUTS.ordinal}
            AND      txr.$CREATED>= (:lastModified)
            ORDER BY txr.$CREATED
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
                    txo.$TRANSACTION_ID, 
                    txo.$LEAF_INDEX, 
                    txr.$CREATED
            FROM {h-schema}utxo_transaction_output txo
            INNER JOIN {h-schema}utxo_relevant_transaction_state txr
            ON  txo.$TRANSACTION_ID = txr.$TRANSACTION_ID
            AND txo.$LEAF_INDEX = txr.$LEAF_INDEX
            AND txo.$GROUP_INDEX = txr.$GROUP_INDEX
            WHERE    txr.$CONSUMED = FALSE
            AND      txo.$GROUP_INDEX = ${UtxoComponentGroup.OUTPUTS.ordinal}
            AND      txo.$TOKEN_TYPE = (:tokenType)
            AND      txo.$TOKEN_ISSUER_HASH = (:issuerHash)
            AND      txo.$TOKEN_NOTARY_X500_NAME = (:notaryX500Name)
            AND      txo.$TOKEN_SYMBOL = (:symbol)
            AND      txr.$CREATED >= (:lastModified)
            ORDER BY txr.$CREATED
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
            SELECT DISTINCT  txo.$TOKEN_TYPE, 
                             txo.$TOKEN_ISSUER_HASH, 
                             txo.$TOKEN_NOTARY_X500_NAME, 
                             txo.$TOKEN_SYMBOL
            FROM {h-schema}utxo_transaction_output txo
            INNER JOIN {h-schema}utxo_relevant_transaction_state txr
            ON  txo.$TRANSACTION_ID = txr.$TRANSACTION_ID
            AND txo.$LEAF_INDEX = txr.$LEAF_INDEX
            AND txo.$GROUP_INDEX = txr.$GROUP_INDEX
            WHERE    txr.$CONSUMED = FALSE
            AND      txr.$GROUP_INDEX = ${UtxoComponentGroup.OUTPUTS.ordinal}
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
