package net.corda.utxo.token.sync.services

import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.TokenRecord
import net.corda.utxo.token.sync.entities.TokenRefRecord
import net.corda.v5.ledger.utxo.StateRef
import java.time.Instant
import javax.persistence.EntityManager

/**
 * The [UtxoTokenRepository] provides read access to the token data stored within the ledger DB
 */
interface UtxoTokenRepository {

    /**
     * Gets a list of spent tokens
     *
     * @param entityManager An instance of the [EntityManager] used for accessing the virtual nodes DB
     * @param stateRefs A list of states to check.
     *
     * @return A list of [TokenRecord]s for any spent tokens that match a state ref in the [stateRefs] list. Any empty
     * list if none are found.
     */
    fun getSpentTokensByRef(entityManager: EntityManager, stateRefs: List<StateRef>): List<TokenRecord>

    /**
     * Gets a list of unspent tokens from a point in time.
     *
     * @param entityManager An instance of the [EntityManager] used for accessing the virtual nodes DB
     * @param startRecordTimestamp The timestamp from which to start selecting records
     * @param maxRecordsToReturn The maximum number of records to read from the given [startRecordTimestamp]
     *
     * @return A list of [TokenRecord]s ordered by [TokenRecord.lastModified], or an empty list if none are
     * found.
     */
    fun getUnspentTokensFromTimestamp(
        entityManager: EntityManager,
        startRecordTimestamp: Instant,
        maxRecordsToReturn: Int
    ): List<TokenRecord>

    /**
     * Gets a list of unspent token references from a point in time.
     *
     * @param entityManager An instance of the [EntityManager] used for accessing the virtual nodes DB
     * @param poolKeyRecord The key for the set of records we need to retrieve
     * @param startRecordTimestamp The timestamp from which to start selecting records
     * @param maxRecordsToReturn The maximum number of records to read from the given [startRecordTimestamp]
     *
     * @return A list of [TokenRefRecord]s ordered by [TokenRecord.lastModified], or an empty list if none
     * are found.
     */
    fun getUnspentTokenRefsFromTimestamp(
        entityManager: EntityManager,
        poolKeyRecord: TokenPoolKeyRecord,
        startRecordTimestamp: Instant,
        maxRecordsToReturn: Int
    ): List<TokenRefRecord>

    /**
     * Gets a distinct list of token pool keys from all unspent tokens.
     *
     * @param entityManager An instance of the [EntityManager] used for accessing the virtual nodes DB
     * @return A distinct set of [TokenPoolKeyRecord] across all unspent tokens in the DB. an empty set if none exist.
     */
    fun getDistinctTokenPools(entityManager: EntityManager):Set<TokenPoolKeyRecord>
}
