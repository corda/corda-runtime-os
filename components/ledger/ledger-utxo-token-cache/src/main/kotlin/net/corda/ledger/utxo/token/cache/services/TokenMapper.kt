package net.corda.ledger.utxo.token.cache.services

import java.sql.SQLException
import javax.persistence.Tuple

interface TokenMapper {
    @Throws(SQLException::class)
    fun map(tuples: List<Tuple>): Map<Int, List<ByteArray>>
}

fun List<Tuple>.mapToToken(mapper: TokenMapper): Map<Int, List<ByteArray>> = mapper.map(this)