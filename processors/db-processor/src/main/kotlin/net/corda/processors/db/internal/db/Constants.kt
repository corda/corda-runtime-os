package net.corda.processors.db.internal.db

internal const val JDBC_URL = "jdbc:postgresql://cluster-db:5432/cordacluster"
internal const val DB_USER = "user"
internal const val DB_PASSWORD = "pass"
internal const val PERSISTENCE_UNIT_NAME = "joel" // TODO - Joel - Choose better persistence unit name.
internal const val MIGRATION_FILE_LOCATION = "migration/db.changelog-master.xml"