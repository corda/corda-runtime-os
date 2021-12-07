package net.corda.p2p.deployment.pods

import java.io.File

class PostGreSql(
    username: String,
    password: String,
    initScript: File?
) : Pod() {
    override val app = "db"
    override val image = "postgres"
    override val ports = listOf(Port("psql", 5432))
    override val environmentVariables = mapOf(
        "POSTGRES_USER" to username,
        "POSTGRES_PASSWORD" to password,
        "POSTGRES_HOST_AUTH_METHOD" to "trust",
    )
    override val rawData = listOf(
        TextRawData(
            "initdb",
            "/docker-entrypoint-initdb.d",
            listOf(
                TextFile(
                    "init.sql",
                    initScript?.readText()
                        ?: ClassLoader.getSystemClassLoader()
                            .getResource("sql/create_table.sql")
                            .readText()
                )
            )
        )
    )

    override val readyLog = ".*database system is ready to accept connections.*".toRegex()
}
