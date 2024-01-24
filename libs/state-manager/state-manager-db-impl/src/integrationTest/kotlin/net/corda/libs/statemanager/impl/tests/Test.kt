package net.corda.libs.statemanager.impl.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.utils.transaction
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.impl.StateManagerImpl
import net.corda.libs.statemanager.impl.metrics.MetricsRecorderImpl
import net.corda.libs.statemanager.impl.repository.impl.PostgresQueryProvider
import net.corda.libs.statemanager.impl.repository.impl.StateRepositoryImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Duration

class Test {
    init {
        System.setProperty("databaseType", "POSTGRES")
        System.setProperty("databaseName", "cordacluster")
    }

    private val dataSource = DbUtils.createDataSource(
        dbUser = "user",
        dbPassword = "YiRS0PUaCq",
//        createSchema = true,
        schemaName = "state_manager",
        maximumPoolSize = 1
    )

    private val stateManager: StateManager = StateManagerImpl(
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>(),
        dataSource = dataSource,
        stateRepository = StateRepositoryImpl(PostgresQueryProvider()),
        metricsRecorder = MetricsRecorderImpl()
    )

    private fun query() = """
        UPDATE state AS s
        SET
            value = temp.value,
            version = s.version + 1,
            metadata = CAST(temp.metadata as JSONB),
            modified_time = CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
        FROM
        (
            VALUES
                (?, ?, ?, ?),
                (?, ?, ?, ?),
                (?, ?, ?, ?),
                (?, ?, ?, ?),
                (?, ?, ?, ?)
        ) AS temp(key, value, metadata, version)
        WHERE temp.key = s.key AND temp.version = s.version;
    """.replace("[\\t\\n\\r]+".toRegex()," ").trim()

    private fun query2() = """
        UPDATE state AS s
        SET
            value = temp.value,
            version = s.version + 1,
            metadata = CAST(temp.metadata as JSONB),
            modified_time = CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
        FROM
        (
            VALUES
                ($1, $2, $3, $4),
                ($5, $6, $7, $8),
                ($9, $10, $11, $12),
                ($13, $14, $15, $16),
                ($17, $18, $19, $20)
        ) AS temp(key, value, metadata, version)
        WHERE temp.key = s.key AND temp.version = s.version;
    """.replace("[\\t\\n\\r]+".toRegex()," ").trim()

    //@Test
    fun initDb() {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/statemanager/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )

        dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
    }

    //@Test
    fun inserData() {
        val value = ByteArray(65 * 1024)
        for (i in 1..20_000) {
            val states = List(4) {
                State(
                    key = "Key-${(i-1)*4+it}",
                    value = value,
                    version = 1
                )
            }
            stateManager.create(states)
            if (i % 10 == 0) println(i)
        }
    }

    @Test
    fun test() {
        val key = 15000
        val value = ByteArray(10)
        val metadata = "{}"
        val version = 1

        for (i in 1..50) {
            val startTime = System.nanoTime()
            dataSource.connection.transaction { con ->
                con.prepareStatement(query()).use {
                    var idx = 1
                    for (values in 1..5) {
                        it.setString(idx++, "Key-${key + i}")
                        it.setBytes(idx++, value)
                        it.setString(idx++, metadata)
                        it.setInt(idx++, version)
                    }
                    it.execute()
                }
            }
            val endTime = System.nanoTime()
            val duration = Duration.ofNanos(endTime - startTime)
            println("Duration: $duration")
        }

//        var psName: String? = null

//        dataSource.connection.use {
//            println("2")
//            it.createStatement().use { stmt ->
//                stmt.executeQuery("select * from pg_prepared_statements where statement ilike 'UPDATE%'")
//                val results = stmt.resultSet
//                while (results.next()) {
//                    val name = results.getObject(1).toString()
//                    psName = name
//                    val statement = results.getObject(2).toString().replace("[\\t\\n\\r]+".toRegex()," ")
//                    val prepareTime = results.getObject(3).toString()
//                    val paramTypes = results.getObject(4).toString()
//                    val fromSql = results.getObject(5)?.toString() ?: ""
//                    val genericPlans = results.getObject(6).toString()
//                    val customPlans = results.getObject(7).toString()
//                    println("name [$name], statement [$statement], prepareTime [$prepareTime], " +
//                            "paramTypes [$paramTypes], fromSql [$fromSql], genericPlans [$genericPlans], " +
//                            "customPlans [$customPlans]")
//                }
//            }
//        }
//        dataSource.connection.use { con ->
//            println("2")
//            val query = "PREPARE ps_test(${List(5) { "text, bytea, text, integer" }.joinToString(", ")}) AS ${query2()}"
//            println(query)
//            con.createStatement().use { stmt ->
//                stmt.executeQuery(query)
//            }
//        }
//
//        dataSource.connection.use {
//            println("3")
//            val query = "EXPLAIN (ANALYZE, BUFFERS) EXECUTE ps_test(${List(5) { "?, ?, ?, ?" }.joinToString(", ")})"
//            println(query)
//            it.prepareStatement(query).use { stmt ->
//                var idx = 1
//                for (values in 1..5) {
//                    stmt.setString(idx++, "Key-$idx")
//                    stmt.setBytes(idx++, value)
//                    stmt.setString(idx++, metadata)
//                    stmt.setInt(idx++, version)
//                }
//                stmt.execute()
//                val results = stmt.resultSet
//                val colCount = results.metaData.columnCount
//
//                while (results.next()) {
//                    val row = (1..colCount).joinToString(separator = ", ") { i ->
//                        val colName = results.metaData.getColumnName(i)
//                        val colValue = results.getObject(i).toString()
//                        "$colName [$colValue]"
//                    }
//                    println("explain: $row")
//                }
//            }
//        }
    }
}