package net.corda.ledger.libs.persistence.common

import java.sql.SQLException
import javax.persistence.Tuple

interface ComponentGroupMapper {
    @Throws(SQLException::class)
    fun map(tuples: List<Tuple>): Map<Int, List<ByteArray>>
}

interface ComponentGroupArrayMapper {
    @Throws(SQLException::class)
    fun mapColumns(tuples: List<Array<Any?>>): Map<Int, List<ByteArray>>
}

fun List<Tuple>.mapToComponentGroups(mapper: ComponentGroupMapper): Map<Int, List<ByteArray>> = mapper.map(this)
fun List<Array<Any?>>.mapToComponentGroups(mapper: ComponentGroupArrayMapper): Map<Int, List<ByteArray>> = mapper.mapColumns(this)
