package net.corda.messaging.db.persistence

import net.corda.messaging.db.util.DbUtils.Companion.createOffsetsTableStmt
import net.corda.messaging.db.util.DbUtils.Companion.createTopicRecordsTableStmt
import net.corda.messaging.db.util.DbUtils.Companion.createTopicsTableStmt
import net.corda.utilities.deleteRecursively
import org.h2.tools.Server
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

class DbAccessProviderH2Test: DbAccessProviderTestBase() {

    lateinit var tempFolder: Path
    private lateinit var server: Server

    private val h2Port = 9090
    private val jdbcUrl = "jdbc:h2:tcp://localhost:$h2Port/test"
    private val username = "sa"
    private val password = ""

    override fun startDatabase() {
        tempFolder = Files.createTempDirectory("h2-database")
        server = Server.createTcpServer("-tcpPort", h2Port.toString(), "-tcpAllowOthers", "-ifNotExists", "-baseDir", tempFolder.toAbsolutePath().toString())
        server.start()
    }

    override fun stopDatabase() {
        server.stop()
        tempFolder.deleteRecursively()
    }

    override fun createTables() {
        val connection = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword())
        connection.prepareStatement(createTopicRecordsTableStmt).execute()
        connection.prepareStatement(createOffsetsTableStmt).execute()
        connection.prepareStatement(createTopicsTableStmt).execute()
    }

    override fun getDbType(): DBType {
        return DBType.H2
    }

    override fun getJdbcUrl(): String {
        return jdbcUrl
    }

    override fun getUsername(): String {
        return username
    }

    override fun getPassword(): String {
        return password
    }

    override fun hasDbConfigured(): Boolean {
        return true
    }
}