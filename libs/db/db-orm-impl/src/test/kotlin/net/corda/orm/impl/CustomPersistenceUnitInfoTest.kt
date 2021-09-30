package net.corda.orm.impl

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.osgi.OsgiClassLoader
import org.hibernate.osgi.OsgiPersistenceProvider
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import javax.persistence.SharedCacheMode
import javax.persistence.ValidationMode
import javax.persistence.spi.PersistenceUnitTransactionType
import javax.sql.DataSource

class CustomPersistenceUnitInfoTest {

    private val datasource = mock<DataSource>()
    private val persistenceUnitInfo = CustomPersistenceUnitInfo(
        "Unit test",
        listOf("one", "two"),
        mapOf("a" to "b").toProperties(),
        datasource
    )

    @Test
    fun `set persistenceUnitName`() {
        assertThat(persistenceUnitInfo.persistenceUnitName).isEqualTo("Unit test")
    }

    @Test
    fun `use OSGi provider`() {
        assertThat(persistenceUnitInfo.persistenceProviderClassName).isEqualTo(OsgiPersistenceProvider::class.java.name)
    }

    @Test
    fun `use resource local tx type`() {
        assertThat(persistenceUnitInfo.transactionType).isEqualTo(PersistenceUnitTransactionType.RESOURCE_LOCAL)
    }

    @Test
    fun `do not use JTA datasource`() {
        assertThat(persistenceUnitInfo.jtaDataSource).isNull()
    }

    @Test
    fun `set non-JTA datasource`() {
        assertThat(persistenceUnitInfo.nonJtaDataSource).isEqualTo(datasource)
    }

    @Test
    fun `set managed class names to entities`() {
        assertThat(persistenceUnitInfo.managedClassNames).containsAll(listOf("one", "two"))
    }

    @Test
    fun `set properties`() {
        assertThat(persistenceUnitInfo.properties).containsEntry("a", "b")
    }

    @Test
    fun `mapping file list is empty`() {
        assertThat(persistenceUnitInfo.mappingFileNames).isEmpty()
    }

    @Test
    fun `jar file list is empty`() {
        assertThat(persistenceUnitInfo.jarFileUrls).isEmpty()
    }

    @Test
    fun `leave persistenceUnitRootUrl null`() {
        assertThat(persistenceUnitInfo.persistenceUnitRootUrl).isNull()
    }

    @Test
    fun `set excludeUnlistedClasses false`() {
        assertThat(persistenceUnitInfo.excludeUnlistedClasses()).isFalse()
    }

    @Test
    fun `leave SharedCacheMode unspecified`() {
        // NOTE: may need to investigate this later
        assertThat(persistenceUnitInfo.sharedCacheMode).isEqualTo(SharedCacheMode.UNSPECIFIED)
    }

    @Test
    fun `set validation mode to none`() {
        assertThat(persistenceUnitInfo.validationMode).isEqualTo(ValidationMode.NONE)
    }

    @Test
    fun `set schema version to 2point2`() {
        assertThat(persistenceUnitInfo.persistenceXMLSchemaVersion).isEqualTo("2.2")
    }

    @Test
    fun `set classLoader to OSGi platform classloader`() {
        assertThat(persistenceUnitInfo.classLoader).isEqualTo(OsgiClassLoader.getPlatformClassLoader())
    }

    @Test
    fun `set new temp classLoader to null`() {
        // NOTE: need to validate that this is correct
        assertThat(persistenceUnitInfo.newTempClassLoader).isNull()
    }
}
