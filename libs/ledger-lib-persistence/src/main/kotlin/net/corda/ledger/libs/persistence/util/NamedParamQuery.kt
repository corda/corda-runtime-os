package net.corda.ledger.libs.persistence.util

import net.corda.ledger.libs.persistence.utxo.impl.UtxoPersistenceServiceImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface NamedParamQuery {
    companion object {
        fun from(sql: String): NamedParamQuery {
            val plainSql = StringBuilder()
            val fields = mutableMapOf<String, Int>()
            var marker: StringBuilder? = null
            var markerIndex = 0
            for (i in sql.indices) {
                val c = sql[i]
                if (c == ':' && (i < sql.length - 1 && sql[i + 1].isValidTokenChar())) {
                    marker = StringBuilder()
                    markerIndex++
                    plainSql.append('?')
                    continue
                }

                if (null != marker) {
                    if (!c.isValidTokenChar()) {
                        fields[marker.toString()] = markerIndex
                        marker = null
                    } else {
                        marker.append(c)
                        continue
                    }
                }
                plainSql.append(c)
            }

            if (null != marker) {
                fields[marker.toString()] = markerIndex
            }

            return NamedParamQueryImpl(plainSql.toString(), fields)
        }

        private fun Char.isValidTokenChar(): Boolean = this.isLetterOrDigit() || this == '_' || this == '-'
    }

    val sql: String
    val fields: Map<String, Int>

    private class NamedParamQueryImpl(
        override val sql: String,
        override val fields: Map<String, Int>,
    ) : NamedParamQuery
}