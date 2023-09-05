package net.corda.orm

enum class DatabaseType(val value: String) {
    POSTGRES(value = "postgres"),
    HSQLDB(value = "hsqldb")
}

interface DatabaseTypeProvider {
    companion object {
        const val DATABASE_TYPE = "database.type"
        const val POSTGRES_TYPE = "$DATABASE_TYPE=postgres"
        const val POSTGRES_TYPE_FILTER = "($POSTGRES_TYPE)"
        const val HSQLDB_TYPE = "$DATABASE_TYPE=hsqldb"
        const val HSQLDB_TYPE_FILTER = "($HSQLDB_TYPE)"
    }

    val databaseType: DatabaseType
}
