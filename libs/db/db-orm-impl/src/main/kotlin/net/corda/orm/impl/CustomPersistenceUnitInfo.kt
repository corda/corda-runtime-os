package net.corda.orm.impl

import org.hibernate.osgi.OsgiClassLoader
import org.hibernate.osgi.OsgiPersistenceProvider
import java.net.URL
import java.util.Properties
import javax.persistence.SharedCacheMode
import javax.persistence.ValidationMode
import javax.persistence.spi.ClassTransformer
import javax.persistence.spi.PersistenceUnitInfo
import javax.persistence.spi.PersistenceUnitTransactionType
import javax.sql.DataSource

@Suppress("TooManyFunctions")
class CustomPersistenceUnitInfo(
    private val persistenceUnitName: String,
    private val managedClassNames: List<String>,
    private val properties: Properties,
    private val nonJtaDataSource: DataSource
) : PersistenceUnitInfo {
    /**
     * Returns the name of the persistence unit. Corresponds to the
     * `name` attribute in the `persistence.xml` file.
     * @return the name of the persistence unit
     */
    override fun getPersistenceUnitName(): String {
        return persistenceUnitName
    }

    /**
     * Returns the fully qualified name of the persistence provider
     * implementation class. Corresponds to the `provider` element in
     * the `persistence.xml` file.
     * @return the fully qualified name of the persistence provider
     * implementation class
     */
    override fun getPersistenceProviderClassName(): String {
        return OsgiPersistenceProvider::class.java.name
    }

    /**
     * Returns the transaction type of the entity managers created by
     * the `EntityManagerFactory`. The transaction type corresponds to
     * the `transaction-type` attribute in the `persistence.xml` file.
     * @return transaction type of the entity managers created
     * by the EntityManagerFactory
     */
    override fun getTransactionType(): PersistenceUnitTransactionType {
        return PersistenceUnitTransactionType.RESOURCE_LOCAL
    }

    /**
     * Returns the JTA-enabled data source to be used by the
     * persistence provider. The data source corresponds to the
     * `jta-data-source` element in the `persistence.xml` file or is
     * provided at deployment or by the container.
     * @return the JTA-enabled data source to be used by the
     * persistence provider
     */
    override fun getJtaDataSource(): DataSource? {
        return null
    }

    /**
     * Returns the non-JTA-enabled data source to be used by the
     * persistence provider for accessing data outside a JTA
     * transaction. The data source corresponds to the named
     * `non-jta-data-source` element in the `persistence.xml` file or
     * provided at deployment or by the container.
     * @return the non-JTA-enabled data source to be used by the
     * persistence provider for accessing data outside a JTA
     * transaction
     */
    override fun getNonJtaDataSource(): DataSource? {
        return nonJtaDataSource
    }

    /**
     * Returns the list of the names of the mapping files that the
     * persistence provider must load to determine the mappings for
     * the entity classes. The mapping files must be in the standard
     * XML mapping format, be uniquely named and be resource-loadable
     * from the application classpath.  Each mapping file name
     * corresponds to a `mapping-file` element in the
     * `persistence.xml` file.
     * @return the list of mapping file names that the persistence
     * provider must load to determine the mappings for the entity
     * classes
     */
    override fun getMappingFileNames(): MutableList<String> {
        return mutableListOf()
    }

    /**
     * Returns a list of URLs for the jar files or exploded jar
     * file directories that the persistence provider must examine
     * for managed classes of the persistence unit. Each URL
     * corresponds to a `jar-file` element in the
     * `persistence.xml` file. A URL will either be a
     * file: URL referring to a jar file or referring to a directory
     * that contains an exploded jar file, or some other URL from
     * which an InputStream in jar format can be obtained.
     * @return a list of URL objects referring to jar files or
     * directories
     */
    override fun getJarFileUrls(): MutableList<URL> {
        return mutableListOf()
    }

    /**
     * Returns the URL for the jar file or directory that is the
     * root of the persistence unit. (If the persistence unit is
     * rooted in the WEB-INF/classes directory, this will be the
     * URL of that directory.)
     * The URL will either be a file: URL referring to a jar file
     * or referring to a directory that contains an exploded jar
     * file, or some other URL from which an InputStream in jar
     * format can be obtained.
     * @return a URL referring to a jar file or directory
     */
    override fun getPersistenceUnitRootUrl(): URL? {
        return null
    }

    /**
     * Returns the list of the names of the classes that the
     * persistence provider must add to its set of managed
     * classes. Each name corresponds to a named `class` element in the
     * `persistence.xml` file.
     * @return the list of the names of the classes that the
     * persistence provider must add to its set of managed
     * classes
     */
    override fun getManagedClassNames(): MutableList<String> {
        return managedClassNames.toMutableList()
    }

    /**
     * Returns whether classes in the root of the persistence unit
     * that have not been explicitly listed are to be included in the
     * set of managed classes. This value corresponds to the
     * `exclude-unlisted-classes` element in the `persistence.xml` file.
     * @return whether classes in the root of the persistence
     * unit that have not been explicitly listed are to be
     * included in the set of managed classes
     */
    override fun excludeUnlistedClasses(): Boolean {
        return false
    }

    /**
     * Returns the specification of how the provider must use
     * a second-level cache for the persistence unit.
     * The result of this method corresponds to the `shared-cache-mode`
     * element in the `persistence.xml` file.
     * @return the second-level cache mode that must be used by the
     * provider for the persistence unit
     *
     * @since Java Persistence 2.0
     */
    override fun getSharedCacheMode(): SharedCacheMode {
        // ??
        return SharedCacheMode.UNSPECIFIED
    }

    /**
     * Returns the validation mode to be used by the persistence
     * provider for the persistence unit.  The validation mode
     * corresponds to the `validation-mode` element in the
     * `persistence.xml` file.
     * @return the validation mode to be used by the
     * persistence provider for the persistence unit
     *
     * @since Java Persistence 2.0
     */
    override fun getValidationMode(): ValidationMode {
        return ValidationMode.NONE
    }

    /**
     * Returns a properties object. Each property corresponds to a
     * `property` element in the `persistence.xml` file
     * or to a property set by the container.
     * @return Properties object
     */
    override fun getProperties(): Properties {
        return properties
    }

    /**
     * Returns the schema version of the `persistence.xml` file.
     * @return persistence.xml schema version
     *
     * @since Java Persistence 2.0
     */
    override fun getPersistenceXMLSchemaVersion(): String {
        return "2.2"
    }

    /**
     * Returns ClassLoader that the provider may use to load any
     * classes, resources, or open URLs.
     * @return ClassLoader that the provider may use to load any
     * classes, resources, or open URLs
     */
    override fun getClassLoader(): ClassLoader {
        return OsgiClassLoader.getPlatformClassLoader()
    }

    /**
     * Add a transformer supplied by the provider that will be
     * called for every new class definition or class redefinition
     * that gets loaded by the loader returned by the
     * [PersistenceUnitInfo.getClassLoader] method. The transformer
     * has no effect on the result returned by the
     * [PersistenceUnitInfo.getNewTempClassLoader] method.
     * Classes are only transformed once within the same classloading
     * scope, regardless of how many persistence units they may be
     * a part of.
     * @param transformer   provider-supplied transformer that the
     * container invokes at class-(re)definition time
     */
    override fun addTransformer(transformer: ClassTransformer?) {
    }

    /**
     * Return a new instance of a ClassLoader that the provider may
     * use to temporarily load any classes, resources, or open
     * URLs. The scope and classpath of this loader is exactly the
     * same as that of the loader returned by [ ][PersistenceUnitInfo.getClassLoader]. None of the classes loaded
     * by this class loader will be visible to application
     * components. The provider may only use this ClassLoader within
     * the scope of the [ ][PersistenceProvider.createContainerEntityManagerFactory] call.
     * @return temporary ClassLoader with same visibility as current
     * loader
     */
    override fun getNewTempClassLoader(): ClassLoader? {
        return null
    }
}
