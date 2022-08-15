package net.corda.ledger.consensual.impl.internal

import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.v5.base.util.contextLogger
import java.time.Instant
import javax.persistence.EntityManager

class ConsensualLedgerDAO(
    val requestId: String,
    val clock: () -> Instant,
    val loadClass: (holdingIdentity: net.corda.virtualnode.HoldingIdentity, fullyQualifiedClassName: String) -> Class<*>,
) {
    companion object {
        private val logger = contextLogger()
    }

    fun persistTransaction(request: PersistTransaction, entityManager: EntityManager): EntityResponse {
        logger.debug("TMP DEBUG 1. request: $request, requestId: $requestId, loadClass: $loadClass")
        val now = clock()
        // we're already inside a transaction
        // do some native queries to insert multiple rows (and across tables)
        val query = entityManager.createNativeQuery("""
            INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)""")
            .setParameter("id", "0")
            .setParameter("privacySalt", arrayOf<Byte>(1))
            .setParameter("accountId", "2")
            .setParameter("createdAt", now)

        // execute query
        val result = query.executeUpdate()

        // construct response
        logger.info("TMP DEBUG 2. result of insert: $result")
        return EntityResponse(now, requestId, EntityResponseSuccess())
    }
}
