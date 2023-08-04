package net.corda.ledger.utxo.token.cache.services

import java.sql.SQLException
import javax.persistence.Tuple
import net.corda.ledger.utxo.token.cache.entities.AvailTokenBucket

interface TokenMapper {
    @Throws(SQLException::class)
    fun map(tuples: List<Tuple>): AvailTokenBucket
}

fun List<Tuple>.mapToToken(mapper: TokenMapper): AvailTokenBucket = mapper.map(this)