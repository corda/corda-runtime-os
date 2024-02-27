package net.corda.db.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

class HikariDataSourceFactoryTest {
    private val dbName = "cordacluster"
    private val connectionsSQL = "SELECT " +
            "query, " +
            "state " +
            "backend_start, " +
            "query_start, " +
            "application_name, " +
            "client_addr " +
            "FROM pg_stat_activity WHERE datname = '$dbName'"

    private val monitorPoolDelegate = lazy {
        DataSourceFactoryImpl().create(
            enablePool = true,
            driverClass = "org.postgresql.Driver",
            jdbcUrl = "jdbc:postgresql://localhost:5432/$dbName",
            username = "postgres",
            password = "password",
            maximumPoolSize = 1,
            minimumPoolSize = null,
            idleTimeout = Duration.ofMinutes(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )
    }
    private val monitorPool by monitorPoolDelegate

    @AfterEach
    fun tearDown() {
        if(monitorPoolDelegate.isInitialized())
            monitorPool.close()
    }

    @Disabled("This test should not be run in the pipeline. It is useful for observing Hikari behaviour for particular" +
            " configuration settings, but it isn't a useful test for corda and will always be time bound.")
    @Suppress("ForEachOnRange")
    @Test
    fun `observe hikari remove idle connections`() {
        val maxConnections = 5
        val minConnections = 0
        val idleTimeout = 10.toDuration(DurationUnit.SECONDS).toJavaDuration()
        DataSourceFactoryImpl().create(
            enablePool = true,
            driverClass = "org.postgresql.Driver",
            jdbcUrl = "jdbc:postgresql://localhost:5432/$dbName",
            username = "postgres",
            password = "password",
            maximumPoolSize = maxConnections,
            minimumPoolSize = minConnections,
            idleTimeout = idleTimeout,
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        ).use { hf ->
            val connections1 = checkConnections()
            println("Pool has $connections1 connections.")
            assertThat(connections1).isEqualTo(0)

            (1..20).toList().parallelStream().forEach {
                hf.connection.use {
                    it.prepareStatement("SELECT NOW();").execute()
                    Thread.sleep(20)
                }
            }

            val connections2 = checkConnections()
            println("Pool has $connections2 connections.")
            assertThat(connections2).isEqualTo(maxConnections)

            val start = Instant.now()
            println("Wait for the connections to go to $minConnections")
            Thread.sleep(idleTimeout.toMillis()) // wait at least as long as idle timeout
            while(
                checkConnections(false) > minConnections
                && start.plusMillis(idleTimeout.toMillis()).plusSeconds(30) > Instant.now()
            ) {
                println("Still ${checkConnections(false)} connections remaining after " +
                        "${Duration.between(start, Instant.now()).seconds}s.")
                Thread.sleep(1000)
            }

            val connections3 = checkConnections()
            println("Pool has $connections3 connections.")
            assertThat(connections3).isEqualTo(minConnections)

            println("Execute another query so we can check we can scale back up from zero")
            hf.connection.use {
                it.prepareStatement("SELECT NOW();").execute()
            }
            val connections4 = checkConnections()
            println("Pool has $connections4 connections.")
            assertThat(connections4).isGreaterThan(minConnections)
        }
    }

    private fun checkConnections(print: Boolean = true): Int {
        monitorPool.connection.use {
            val results = it.prepareStatement(connectionsSQL).executeQuery()
            val meta = results.metaData
            var connections = 0
            while (results.next()) {
                if(results.getString(1) == connectionsSQL)
                    continue

                connections++

                if(print) {
                    val row = (1..meta.columnCount).fold("") { s, i ->
                        "$s|${meta.getColumnName(i)}=${results.getObject(i)}"
                    }
                    println(row)
                }
            }
            return connections
        }
    }
}