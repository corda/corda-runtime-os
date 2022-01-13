package net.corda.p2p.deployment.pods

class PostGreSql(
    dbDetails: DbDetails,
) : Pod() {
    override val app = "db"
    override val image = "postgres"
    override val ports = listOf(Port.Psql)
    override val environmentVariables = mapOf(
        "POSTGRES_USER" to dbDetails.username,
        "POSTGRES_PASSWORD" to dbDetails.password,
        "POSTGRES_HOST_AUTH_METHOD" to "trust",
    )
    override val rawData = listOf(
        TextRawData(
            "initdb",
            "/docker-entrypoint-initdb.d",
            listOf(
                TextFile(
                    "init.sql",
                    dbDetails.sqlInitFile?.readText()
                        ?: ClassLoader.getSystemClassLoader()
                            .getResource("sql/create_table.sql")
                            .readText()
                )
            )
        )
    )

    override val readyLog = ".*database system is ready to accept connections.*".toRegex()
}
