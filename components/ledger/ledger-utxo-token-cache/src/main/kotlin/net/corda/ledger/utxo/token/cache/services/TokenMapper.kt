package net.corda.ledger.utxo.token.cache.services

import java.sql.SQLException
import javax.persistence.Tuple
import net.corda.ledger.utxo.token.cache.entities.CachedToken

interface TokenMapper {
    @Throws(SQLException::class)
    fun map(tuples: List<Tuple>): CachedToken
}

fun List<Tuple>.mapToToken(mapper: TokenMapper): CachedToken = mapper.map(this)