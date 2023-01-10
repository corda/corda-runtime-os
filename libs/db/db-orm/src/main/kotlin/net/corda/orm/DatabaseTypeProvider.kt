package net.corda.orm

interface DatabaseTypeProvider {
    val databaseType: DatabaseType
}

enum class DatabaseType {
    POSTGRESQL,
    HSQLDB
}
