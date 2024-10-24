package net.corda.ledger.libs.persistence.util

import net.corda.crypto.core.InvalidParamsException
import net.corda.ledger.libs.persistence.utxo.impl.UtxoPersistenceServiceImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

class NamedParamStatement(
    private val namedParamQuery: NamedParamQuery,
    private val conn: Connection,
) : AutoCloseable {
    private companion object {
        val tzUTC: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val log: Logger = LoggerFactory.getLogger(UtxoPersistenceServiceImpl::class.java)
    }
    private val statement: PreparedStatement = conn.prepareStatement(namedParamQuery.sql)

    override fun close() {
        statement.close()
    }

    fun executeQuery(): ResultSet {
        if(log.isDebugEnabled) log.debug("Execute Query: $statement")
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
        if(log.isDebugEnabled) log.debug("Execute Update: $statement")

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
