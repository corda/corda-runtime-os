package net.corda.ledger.libs.persistence.util

import net.corda.crypto.core.InvalidParamsException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

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

class NamedParamStatement(
    private val namedParamQuery: NamedParamQuery,
    private val conn: Connection,
) : AutoCloseable {
    companion object {
        val tzUTC: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }
    private val statement: PreparedStatement = conn.prepareStatement(namedParamQuery.sql)

    override fun close() {
        statement.close()
    }

    fun executeQuery(): ResultSet {
        println("############ $statement")
        return statement.executeQuery()
    }

    fun <T> executeQueryAsList(mapper: (ResultSet) -> T): List<T> {
        val result = mutableListOf<T>()
        executeQuery().use { rs ->
            while (rs.next()) {
                result.add(mapper(rs))
            }
        }
        return result
    }

    fun executeQueryAsListOfColumns(): List<Array<Any?>> {
        val result = mutableListOf<Array<Any?>>()
        executeQuery().use { rs ->
            val colCount = rs.metaData.columnCount
            while (rs.next()) {
                val cols = arrayOfNulls<Any>(colCount)
                for (i in 0 until colCount) {
                    cols[i] = rs.getObject(i + 1)
                }
                result.add(cols)
            }
        }
        return result
    }

    fun executeUpdate(): Int {
        println("############ $statement")
        return statement.executeUpdate()
    }

    fun setInt(name: String, value: Int) {
        statement.setInt(
            getFieldIndex(name),
            value
        )
    }

    fun setStrings(name: String, values: List<String>) {
        val stringsArray = conn.createArrayOf("varchar", values.toTypedArray())
        println("#### filtering: $values")
        statement.setArray(
            getFieldIndex(name),
            stringsArray
        )
    }

    fun setInts(name: String, values: List<Int>) {
        val intsArray = conn.createArrayOf("int", values.toTypedArray())
        statement.setArray(
            getFieldIndex(name),
            intsArray
        )
    }

    fun setString(name: String, value: String) {
        statement.setString(
            getFieldIndex(name),
            value
        )
    }

    fun setInstant(name: String, value: Instant) {
        statement.setTimestamp(
            getFieldIndex(name),
            Timestamp.from(value),
            tzUTC
        )
    }

    fun setBytes(name: String, value: ByteArray) {
        statement.setBytes(
            getFieldIndex(name),
            value
        )
    }

    private fun getFieldIndex(name: String) =
        namedParamQuery.fields[name] ?: throw InvalidParamsException("Field $name not found")
}
