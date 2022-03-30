package net.corda.orm.impl

import net.corda.db.core.CloseableDataSource
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.TransactionIsolationLevel
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.net.URLClassLoader
import javax.persistence.spi.PersistenceUnitInfo

class EntityManagerFactoryFactoryImplTest {
    data class TestEntity(val name: String)
    data class AnotherTestEntity(val name: String)

    private val builder = mock<EntityManagerFactoryBuilder> {
        on { build() } doReturn(mock())
    }
    private val datasource = mock<CloseableDataSource>()
    private val config = mock<EntityManagerConfiguration> {
        on { dataSource } doReturn datasource
        on { showSql } doReturn true
        on { formatSql } doReturn true
        on { transactionIsolationLevel } doReturn TransactionIsolationLevel.READ_COMMITTED
        on { ddlManage } doReturn DdlManage.UPDATE
        on { jdbcTimezone } doReturn "OOF"
    }
    private val mockBuilder = mock<(p: PersistenceUnitInfo) -> EntityManagerFactoryBuilder>() {
        on { invoke(any()) } doReturn(builder)
    }

    @Test
    fun `when create set persistenceUnitName`() {
        EntityManagerFactoryFactoryImpl(mockBuilder).create(
            "Unit Test", listOf(TestEntity::class.java), config
        )
        verify(mockBuilder)(
            check {
                assertThat(it.persistenceUnitName).isEqualTo("Unit Test")
            }
        )
    }

    @Test
    fun `when create set properties`() {
        EntityManagerFactoryFactoryImpl(mockBuilder).create(
            "Unit Test", listOf(TestEntity::class.java), config
        )
        verify(mockBuilder)(
            check {
                assertThat(it.properties).containsEntry("hibernate.show_sql", "true")
                assertThat(it.properties).containsEntry("hibernate.format_sql", "true")
                assertThat(it.properties).containsEntry("hibernate.connection.isolation", "2")
                assertThat(it.properties).containsEntry("hibernate.hbm2ddl.auto", "update")
                assertThat(it.properties).containsEntry("hibernate.jdbc.time_zone", "OOF")
                assertThat(it.properties).containsEntry("javax.persistence.validation.mode", "none")
            }
        )
    }

    @Test
    fun `when create set entity list`() {
        EntityManagerFactoryFactoryImpl(mockBuilder).create(
            "Unit Test", listOf(TestEntity::class.java, AnotherTestEntity::class.java), config
        )
        verify(mockBuilder)(
            check {
                assertThat(it.managedClassNames).containsAll(
                    listOf(TestEntity::class.java.canonicalName, AnotherTestEntity::class.java.canonicalName)
                )
            }
        )
    }

    @Test
    fun `when create set datasource`() {
        EntityManagerFactoryFactoryImpl(mockBuilder).create(
            "Unit Test", listOf(TestEntity::class.java), config
        )
        verify(mockBuilder)(
            check {
                assertThat(it.nonJtaDataSource).isEqualTo(datasource)
            }
        )
    }

    @Test
    fun `when createEntityManagerFactory set classloaders`() {
        // use multiple class loaders and also ensure we de-dupe them when adding multiple entities classloaders
        val originalEntity = AnotherTestEntity::class.java
        val newClassLoader = URLClassLoader(
            arrayOf(originalEntity.protectionDomain.codeSource.location),
            originalEntity.classLoader.parent
        )
        val newEntity = newClassLoader.loadClass(originalEntity.name)
        EntityManagerFactoryFactoryImpl(mockBuilder).create(
            "Unit Test",
            listOf(TestEntity::class.java, AnotherTestEntity::class.java, newEntity),
            config
        )
        verify(mockBuilder)(
            check {
                assertThat(it.properties).containsEntry(
                    "hibernate.classLoaders",
                    listOf(TestEntity::class.java.classLoader, newClassLoader)
                )
            }
        )
    }
}
