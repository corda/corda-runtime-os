package net.corda.uniqueness.backingstore.impl

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.testkit.DbUtils
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.JpaEntitiesRegistryImpl
import net.corda.uniqueness.backingstore.jpa.datamodel.JPABackingStoreEntities
import org.junit.jupiter.api.*
import org.mockito.kotlin.*
import javax.persistence.EntityExistsException
import javax.persistence.EntityManagerFactory
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplIntegrationTests {
    private lateinit var backingStoreImpl: JPABackingStoreImpl
    private val entityManagerFactory: EntityManagerFactory
    private val dbConfig = DbUtils.getEntityManagerConfiguration("uniqueness_default")

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()

    init {
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            "uniqueness_default",
            JPABackingStoreEntities.classes.toList(),
            dbConfig
        )
    }


    @BeforeEach
    fun init() {
        backingStoreImpl = JPABackingStoreImpl(lifecycleCoordinatorFactory,
            JpaEntitiesRegistryImpl(),
            mock<DbConnectionManager>().apply {
                whenever(getOrCreateEntityManagerFactory(any(), any(), any())) doReturn entityManagerFactory
                whenever(getClusterDataSource()) doReturn dbConfig.dataSource
            })

        backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())
    }

    @Nested
    inner class TransactionTests {
        @Test
        fun `Executing transaction retries upon expected exceptions`() {
            val maxRetryCount = 10
            var execCounter = 0
            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ ->
                        execCounter++
                        throw EntityExistsException()
                    }
                }
            }
            assertEquals(maxRetryCount, execCounter)
        }

        @Test
        fun `Executing transaction does not retry upon unexpected exception`() {

        }

        @Test
        fun `Executing transaction succeeds after a trasient failure`() {

        }
    }

    // TODO: may overlap some existing tests. check if these are useful.
    @Nested
    inner class PersistingDataTests {
        @Test
        fun `Persisting transaction details succeeds`() {

        }

        @Test
        fun `Persisting unconsumed stats succeeds`() {

        }

        @Test
        fun `Consuming data updates unconsued states successfully`() {

        }
    }

    // DB error handling

    @Nested
    inner class TimeoutTests {
        @Test
        fun `Persisting data succeeds within timeout`() {

        }

        @Test
        fun `Persisting data fails after timeout`() {
        }
    }
}