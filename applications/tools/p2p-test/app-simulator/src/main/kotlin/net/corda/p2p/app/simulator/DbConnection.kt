package net.corda.p2p.app.simulator

import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedDeque

class DbConnection(
    dbParams: DBParams?,
    sql: String,
): AutoCloseable {
    private val resources = ConcurrentLinkedDeque<AutoCloseable>()
    private val connections = ThreadLocal.withInitial {
        if(dbParams != null) {
            val properties = Properties()
            properties.setProperty("user", dbParams.username)
            properties.setProperty("password", dbParams.password)
            // DriverManager uses internally Class.forName(), which doesn't work within OSGi by default.
            // This is why we force-load the driver here. For example, see:
            // http://hwellmann.blogspot.com/2009/04/jdbc-drivers-in-osgi.html
            // https://stackoverflow.com/questions/54292876/how-to-use-mysql-in-osgi-application-with-maven
            org.postgresql.Driver()
            DriverManager.getConnection("jdbc:postgresql://${dbParams.host}/${dbParams.db}", properties).also {
                resources.add(it)
                it.autoCommit = false
            }
        } else {
            null
        }
    }
    private val statements = ThreadLocal.withInitial {
        connection?.prepareStatement(sql)?.also {
            resources.add(it)
        }
    }

    val connection by lazy {
        connections.get()
    }
    val statement by lazy {
        statements.get()
    }

    override fun close() {
        resources.forEach {
            it.close()
        }
    }

}