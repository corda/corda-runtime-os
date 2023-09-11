package net.corda.db.testkit.dbutilsimpl

import java.sql.Connection

fun getPropertyNonBlank(key: String, defaultValue: String): String {
    val value = System.getProperty(key)
    return if (value.isNullOrBlank()) {
        defaultValue
    } else {
        value
    }
}

fun Connection.createSchema(schemaName: String?) {
    requireNotNull(schemaName)
    this.use { conn ->
        conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
        conn.commit()
    }
}