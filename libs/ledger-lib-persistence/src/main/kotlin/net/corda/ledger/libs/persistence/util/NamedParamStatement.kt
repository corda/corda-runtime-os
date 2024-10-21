package net.corda.ledger.libs.persistence.util

import net.corda.crypto.core.InvalidParamsException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

// TODO: could be moved somewhere centrally?
interface NamedParamQuery {
    companion object {
        fun from(sql: String) : NamedParamQuery {
            val plainSql = StringBuilder()
            val fields = mutableMapOf<String, Int>()
            var marker: StringBuilder? = null
            var markerIndex = 0
            for(c in sql) {
                if(c == ':') {
                    marker = StringBuilder()
                    markerIndex++
                    plainSql.append('?')
                    continue
                }

                if(null != marker) {
                    if(!c.isLetterOrDigit()) {
                        fields[marker.toString()] = markerIndex
                        marker = null
                    } else {
                        marker.append(c)
                        continue
                    }
                }
                plainSql.append(c)
            }

            if(null != marker) {
                fields[marker.toString()] = markerIndex
            }

            return NamedParamQueryImpl(plainSql.toString(), fields)
        }
    }

    val sql: String
    val fields: Map<String, Int>

    private class NamedParamQueryImpl(
        override val sql: String, override val fields: Map<String, Int>) : NamedParamQuery
}

class NamedParamStatement(
    private val namedParamQuery: NamedParamQuery,
    private val conn: Connection) : AutoCloseable {
    private val statement: PreparedStatement = conn.prepareStatement(namedParamQuery.sql)

    override fun close() {
        statement.close()
    }

    fun executeQuery(): ResultSet {
        return statement.executeQuery()
    }

    fun setInt(name: String, value: Int) {
        statement.setInt(
            getFieldIndex(name),
            value)
    }

    fun setStrings(name: String, transactionIds: List<String>) {
        val stringsArray = conn.createArrayOf("varchar", transactionIds.toTypedArray())
        statement.setArray(
            getFieldIndex(name),
            stringsArray
        )
    }

    private fun getFieldIndex(name: String) =
        namedParamQuery.fields[name]?: throw InvalidParamsException("Field $name not found")
}
