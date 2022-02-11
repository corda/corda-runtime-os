package net.corda.libs.permissions.storage.common

object ConfigKeys {
    // Similar to `net.corda.processors.db.internal.ConstantsKt.CONFIG_JDBC_URL`
    // TODO - remove these when integrating with DbConnectionManager
    const val DB_CONFIG_KEY = "corda.db"
    const val DB_URL = "database.jdbc.url"
    const val DB_USER = "database.user"
    const val DB_PASSWORD = "database.pass"
}