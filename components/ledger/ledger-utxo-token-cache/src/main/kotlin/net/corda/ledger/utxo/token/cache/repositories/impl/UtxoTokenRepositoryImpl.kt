package net.corda.ledger.utxo.token.cache.repositories.impl

import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.utxo.token.cache.repositories.UtxoTokenRepository
import net.corda.ledger.utxo.token.cache.services.UtxoTokenMapper
import net.corda.ledger.utxo.token.cache.services.mapToToken

@Suppress("TooManyFunctions")
/**
 * Reads and writes ledger transaction data to and from the virtual node vault database.
 * The component only exists to be created inside a PERSISTENCE sandbox. We denote it
 * as "corda.marker.only" to force the sandbox to create it, despite it implementing
 * only the [UsedByPersistence] marker interface.
 */
@Component(
    service = [ UtxoTokenRepository::class],
    scope = PROTOTYPE
)
class UtxoTokenRepositoryImpl @Activate constructor() : UtxoTokenRepository
{
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }


    override fun findTokens(
        entityManager: EntityManager,
        poolKey: TokenPoolCacheKey,
        ownerHash: String?,
        regexTag: String?
    ) {
        val queryStrBuilder = StringBuilder()
            .append(
                """
                SELECT
                    transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash,
                    token_symbol, token_tag, token_owner_hash, token_amount, created
                FROM {h-schema}utxo_transaction_output
                WHERE token_type = :tokenType AND
                      token_issuer_hash = :issuerHash AND
                      token_symbol = :symbol
                """.trimIndent()
            )

        if(ownerHash != null) {
            queryStrBuilder.append(" ").append("""
                AND token_owner_hash = :ownerHash
            """.trimIndent())
        }

        if(regexTag != null) {
            queryStrBuilder.append(" ").append(
                """
                    AND token_tag ~ :regexTag
                """.trimIndent()
            )
        }

        val query = entityManager.createNativeQuery(queryStrBuilder.toString(), Tuple::class.java)
            .setParameter("tokenType", poolKey.tokenType)
            .setParameter("issuerHash", poolKey.issuerHash)
            .setParameter("symbol", poolKey.symbol)

        if(ownerHash != null) {
            query.setParameter("ownerHash", ownerHash)
        }

        if(regexTag != null) {
            query.setParameter("regexTag", regexTag)
        }

        val resultList = query.resultListAsTuples().mapToToken(UtxoTokenMapper())
        logger.info("Filipe: $resultList")

    }

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
