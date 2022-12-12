package net.corda.simulator.runtime.persistence

import net.corda.simulator.entities.ConsensualTransactionEntity
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URL
import java.util.Collections
import java.util.Properties
import javax.persistence.SharedCacheMode
import javax.persistence.ValidationMode
import javax.persistence.spi.ClassTransformer
import javax.persistence.spi.PersistenceUnitInfo
import javax.persistence.spi.PersistenceUnitTransactionType
import javax.sql.DataSource

/**
 * A JPA [PersistenceUnitInfo] for Hibernate.
 */
@Suppress("TooManyFunctions")
class JpaPersistenceUnitInfo : PersistenceUnitInfo {
    override fun getPersistenceUnitName(): String = "SimulatorPersistenceUnit"

    override fun getPersistenceProviderClassName(): String = "org.hibernate.jpa.HibernatePersistenceProvider"

    override fun getTransactionType(): PersistenceUnitTransactionType = PersistenceUnitTransactionType.RESOURCE_LOCAL

    override fun getJtaDataSource(): DataSource? = null

    override fun getNonJtaDataSource(): DataSource? = null

    override fun getMappingFileNames(): MutableList<String> = mutableListOf()

    override fun getJarFileUrls(): MutableList<URL> {
        try {
            return Collections.list(
                this.javaClass
                    .classLoader
                    .getResources("")
            );
        } catch (e: IOException) {
            throw UncheckedIOException (e);
        }
    }

    override fun getPersistenceUnitRootUrl(): URL? = null

    override fun getManagedClassNames(): MutableList<String> {
        return ConsensualTransactionEntity.CONSENSUAL_STATES_PERSISTENCE_CLASSES.toMutableList()
    }

    override fun excludeUnlistedClasses(): Boolean = false

    override fun getSharedCacheMode(): SharedCacheMode? = null

    override fun getValidationMode(): ValidationMode? = null

    override fun getProperties(): Properties = Properties()

    override fun getPersistenceXMLSchemaVersion(): String ? = null

    override fun getClassLoader(): ClassLoader? = null

    override fun addTransformer(transformer: ClassTransformer?) {}

    override fun getNewTempClassLoader(): ClassLoader? = null

}