package net.corda.ledger.persistence.consensual

import java.sql.SQLException
import javax.persistence.Tuple

interface TuplesMapper<T> {
    @Throws(SQLException::class)
    fun map(tuples: List<Tuple>): List<T>
}

fun <T> List<Tuple>.mapTuples(mapper: TuplesMapper<T>) = mapper.map(this)