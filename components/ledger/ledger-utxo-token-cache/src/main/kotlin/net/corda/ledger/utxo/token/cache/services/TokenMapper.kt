package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import java.sql.SQLException
import javax.persistence.Tuple

interface TokenMapper {
    @Throws(SQLException::class)
    fun map(tuples: List<Tuple>): Collection<CachedToken>
}

fun List<Tuple>.mapToToken(mapper: TokenMapper): Collection<CachedToken> = mapper.map(this)
