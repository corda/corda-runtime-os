package net.corda.ledger.utxo.token.cache.repositories.impl

import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.queries.SqlQueryProvider
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens.Companion.SQL_PARAMETER_ISSUER_HASH
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens.Companion.SQL_PARAMETER_OWNER_HASH
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens.Companion.SQL_PARAMETER_SYMBOL
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens.Companion.SQL_PARAMETER_TAG_FILTER
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens.Companion.SQL_PARAMETER_TOKEN_NOTARY_X500_NAME
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens.Companion.SQL_PARAMETER_TOKEN_TYPE
import net.corda.ledger.utxo.token.cache.repositories.UtxoTokenRepository
import net.corda.ledger.utxo.token.cache.services.UtxoTokenMapper
import net.corda.ledger.utxo.token.cache.services.mapToToken
import net.corda.v5.ledger.utxo.token.selection.Strategy
import java.math.BigDecimal
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple

class UtxoTokenRepositoryImpl(
    private val sqlQueryProvider: SqlQueryProvider,
) : UtxoTokenRepository {

    override fun findTokens(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?,
        maxTokens: Int,
        strategy: Strategy?
    ): AvailTokenQueryResult {
        val sqlQuery = sqlQueryProvider.getPagedSelectQuery(
            maxTokens,
            regexTag != null,
            ownerHash != null,
            strategy
        )

        val query = entityManager.createNativeQuery(sqlQuery, Tuple::class.java)
            .setParameter(SQL_PARAMETER_TOKEN_TYPE, poolKey.tokenType)
            .setParameter(SQL_PARAMETER_ISSUER_HASH, poolKey.issuerHash)
            .setParameter(SQL_PARAMETER_SYMBOL, poolKey.symbol)
            .setParameter(SQL_PARAMETER_TOKEN_NOTARY_X500_NAME, poolKey.notaryX500Name)

        setParameterIfNecessaryOwnerHash(ownerHash, query)
        setParameterIfNecessaryRegexTag(regexTag, query)

        val tokens = query.resultListAsTuples().mapToToken(UtxoTokenMapper())

        return AvailTokenQueryResult(poolKey, tokens)
    }

    override fun queryBalance(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?
    ): BigDecimal {
        val sqlQuery = sqlQueryProvider.getBalanceQuery(
            regexTag != null,
            ownerHash != null
        )

        val query = entityManager.createNativeQuery(sqlQuery, Tuple::class.java)
            .setParameter(SQL_PARAMETER_TOKEN_TYPE, poolKey.tokenType)
            .setParameter(SQL_PARAMETER_ISSUER_HASH, poolKey.issuerHash)
            .setParameter(SQL_PARAMETER_SYMBOL, poolKey.symbol)
            .setParameter(SQL_PARAMETER_TOKEN_NOTARY_X500_NAME, poolKey.notaryX500Name)

        setParameterIfNecessaryOwnerHash(ownerHash, query)
        setParameterIfNecessaryRegexTag(regexTag, query)

        return query.resultListAsTuples().first()[0] as BigDecimal
    }

    private fun setParameterIfNecessaryOwnerHash(ownerHash: String?, query: Query) {
        if (ownerHash != null) {
            query.setParameter(SQL_PARAMETER_OWNER_HASH, ownerHash)
        }
    }

    private fun setParameterIfNecessaryRegexTag(regexTag: String?, query: Query) {
        if (regexTag != null) {
            query.setParameter(SQL_PARAMETER_TAG_FILTER, regexTag)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
