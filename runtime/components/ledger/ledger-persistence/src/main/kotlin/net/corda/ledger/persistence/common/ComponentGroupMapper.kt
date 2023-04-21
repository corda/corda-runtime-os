package net.corda.ledger.persistence.common

import java.sql.SQLException
import javax.persistence.Tuple

interface ComponentGroupMapper {
    @Throws(SQLException::class)
    fun map(tuples: List<Tuple>): Map<Int, List<ByteArray>>
}

fun List<Tuple>.mapToComponentGroups(mapper: ComponentGroupMapper): Map<Int, List<ByteArray>> = mapper.map(this)