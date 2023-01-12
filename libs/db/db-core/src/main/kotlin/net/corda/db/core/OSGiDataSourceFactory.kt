package net.corda.db.core

import org.osgi.framework.FrameworkUtil
import org.osgi.service.jdbc.DataSourceFactory
import java.sql.SQLException
import java.util.Properties
import javax.sql.DataSource

/**
 * OSGi has its own service that returns instances of [DataSource] so we need to use that.  We *must* use the
 * OSGi service registry instead of the jvm [java.sql.DriverManager].
 *
 * OSGi declares the [DataSourceFactory] interface.  Driver bundles are mostly custom-wrapped vanilla jdbc drivers:
 *
 * * vanilla jdbc drivers do not implement this OSGi interface (except for the Postgres jdbc driver which is an
 * OSGi bundle and implements [PGDataSourceFactory])
 * * have no concept of the OSGi component lifecycle and therefore do not self-register with the OSGi service
 * registry.
 *
 * To register an implementation of [DataSourceFactory] for a given jdbc driver in the OSGi registry,
 * we use [pax-jdbc](https://github.com/ops4j/org.ops4j.pax.jdbc/tree/main/pax-jdbc)
 * which is a bundle that hooks into OSGi events.
 *
 * When a new _bundle_ is installed (or started), either the jdbc bundle, or mostly likely a second 'custom' bundle that
 * explicitly implements [DataSourceFactory] for a given jdbc driver,
 * an OSGi event is raised, and the `pax-jdbc` bundle will scan for
 * instances of the [DataSourceFactory] and register them in the OSGi service registry.
 *
 * As a non OSGi-jdbc driver is _wrapped_ to be a bundle, we need an additional bundle that
 * implements [DataSourceFactory] for that vendor's jdbc driver.
 *
 * The `pax-jdbc` project provides [DataSourceFactory] implementations for
 * popular drivers: https://github.com/ops4j/org.ops4j.pax.jdbc
 *
 * See also:
 *
 * * [pax-jdbc question on stackoverflow](https://stackoverflow.com/questions/42161261/creating-postgresql-datasource-via-pax-jdbc-config-file-on-karaf-4)
 * * [karaf](https://access.redhat.com/documentation/ko-kr/red_hat_fuse/7.2/html/apache_karaf_transaction_guide/using-jdbc-data-sources#doc-wrapper)
 */
@Suppress("MaxLineLength")
object OSGiDataSourceFactory {
    /**
     * Create a [DataSource] using the OSGi [org.osgi.service.jdbc.DataSourceFactory] service.
     *
     * We simply create a data source using OSGi, and pass that into Hikari, which accepts an _existing_ data source
     * rather than _create_ one.
     *
     * The [driverClass] name is what is used to locate the driver in the OSGi service registry.
     *
     * Since we're likely to use the [pax-jdbc] wrappers, this is usually what you'd expect, but you can find in the
     * `Activator` class, in any given driver, for example:
     *
     * * [Oracle activator](https://github.com/ops4j/org.ops4j.pax.jdbc/blob/main/pax-jdbc-oracle/src/main/java/org/ops4j/pax/jdbc/oracle/impl/Activator.java#L31)
     * * [DB2 activator](https://github.com/ops4j/org.ops4j.pax.jdbc/blob/main/pax-jdbc-db2/src/main/java/org/ops4j/pax/jdbc/db2/impl/Activator.java#L31)
     * * [mysql activator](https://github.com/ops4j/org.ops4j.pax.jdbc/blob/main/pax-jdbc-mysql/src/main/java/org/ops4j/pax/jdbc/mysql/impl/Activator.java#L34)
     *
     * and so on.
     *
     * @param driverClass driver class name to be created
     * @param jdbcUrl jdbc url
     * @param username username
     * @param password password
     * @return [DataSource] a data source
     *
     * @throws [SQLException] if it could not get the driver, or could not create data source.
     */
    @Suppress("ThrowsCount")
    @Throws(SQLException::class)
    fun create(driverClass: String, jdbcUrl: String, username: String, password: String): DataSource {
        val bundle = FrameworkUtil.getBundle(this::class.java)
            ?: throw UnsupportedOperationException("No OSGi framework")
        val bundleContext = bundle.bundleContext

        // This is the driver CLASS name.
        val refs = bundleContext.getServiceReferences(
            DataSourceFactory::class.java, "(${DataSourceFactory.OSGI_JDBC_DRIVER_CLASS}=$driverClass)"
        )

        // We could also additionally use:
        // DataSourceFactory.OSGI_JDBC_DRIVER_NAME

        if (refs == null || refs.isEmpty()) {
            throw SQLException("No drivers for JDBC classes are loaded, have you specified -ddatabase.jdbc.directory? Or have you forgotten a pax-jdbc jar?")
        }

        val dsf: DataSourceFactory = bundleContext.getService(refs.max())!!
        val props = Properties()
        props[DataSourceFactory.JDBC_URL] = jdbcUrl
        props[DataSourceFactory.JDBC_USER] = username
        props[DataSourceFactory.JDBC_PASSWORD] = password
        return dsf.createDataSource(props) ?: throw SQLException("Could not create Datasource for $driverClass")
    }
}
