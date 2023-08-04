package net.corda.ledger.utxo.token.cache.repositories.impl

import java.math.BigDecimal
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple
import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
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
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?
    ): AvailTokenQueryResult {

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

        addFilterIfNecessaryOwnerHash(ownerHash, queryStrBuilder)
        addFilterIfNecessaryRegexTag(regexTag, queryStrBuilder)

        val query = entityManager.createNativeQuery(queryStrBuilder.toString(), Tuple::class.java)
            .setParameter("tokenType", poolKey.tokenType)
            .setParameter("issuerHash", poolKey.issuerHash)
            .setParameter("symbol", poolKey.symbol)

        setParameterIfNecessaryOwnerHash(ownerHash, query)
        setParameterIfNecessaryRegexTag(regexTag, query)

        val availTokenBucket = query.resultListAsTuples().mapToToken(UtxoTokenMapper())
        logger.info("Filipe: $availTokenBucket")

        return AvailTokenQueryResult(poolKey, listOf(availTokenBucket))

    }

    override fun queryAvailableBalance(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?,
        stateRefClaimedTokens: Collection<String>
    ): BigDecimal {
        return BigDecimal(1)
    }

    override fun queryTotalBalance(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?
    ): BigDecimal {
        return BigDecimal(1)
    }

    private fun addFilterIfNecessaryOwnerHash(ownerHash: String?, queryStrBuilder: StringBuilder) {
        if(ownerHash != null) {
            queryStrBuilder.append(" ").append("""
                AND token_owner_hash = :ownerHash
            """.trimIndent())
        }
    }

    private fun addFilterIfNecessaryRegexTag(regexTag: String?, queryStrBuilder: StringBuilder) {
        if(regexTag != null) {
            queryStrBuilder.append(" ").append(
                """
                    AND token_tag ~ :regexTag
                """.trimIndent()
            )
        }
    }

    private fun setParameterIfNecessaryOwnerHash(ownerHash: String?, query: Query) {
        if(ownerHash != null) {
            query.setParameter("ownerHash", ownerHash)
        }
    }

    private fun setParameterIfNecessaryRegexTag(regexTag: String?, query: Query) {
        if(regexTag != null) {
            query.setParameter("regexTag", regexTag)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
