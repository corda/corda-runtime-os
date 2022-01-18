package net.corda.permissions.storage.writer.internal

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.permissions.storage.common.ConfigKeys
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.persistence.EntityManagerFactory

class PermissionStorageWriterServiceEventHandlerTest {

    private val subscription = mock<RPCSubscription<PermissionManagementRequest, PermissionManagementResponse>>()
    private val subscriptionFactory = mock<SubscriptionFactory>().apply {
        whenever(createRPCSubscription(any(), any(), any<PermissionStorageWriterProcessor>())).thenReturn(subscription)
    }
    private val permissionStorageWriterProcessor = mock<PermissionStorageWriterProcessor>()
    private val permissionStorageWriterProcessorFactory = mock<PermissionStorageWriterProcessorFactory>().apply {
        whenever(create(any(), any())).thenReturn(permissionStorageWriterProcessor)
    }
    private val readerService = mock<PermissionStorageReaderService>().apply {
        whenever(permissionStorageReader).thenReturn(mock())
    }

    private val allEntitiesSets = listOf(mock<EntitiesSet>().apply {
        whenever(name).thenReturn(DbSchema.RPC_RBAC)
    })

    private val handler = PermissionStorageWriterServiceEventHandler(
        subscriptionFactory,
        permissionStorageWriterProcessorFactory,
        readerService,
        mock(),
        mock(),
        allEntitiesSets,
        entityManagerFactoryCreationFn = ::testObtainEntityManagerFactory
    )

    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())
    private val config = configFactory.create(
        ConfigFactory.empty()
            .withValue(
                ConfigKeys.DB_CONFIG_KEY,
                ConfigValueFactory.fromMap(
                    mapOf(
                        ConfigKeys.DB_URL to "dbUrl",
                        ConfigKeys.DB_USER to "dbUser",
                        ConfigKeys.DB_PASSWORD to "dbPass"
                    )
                )
            )
    )

    private val bootstrapConfig = mapOf(ConfigKeys.BOOTSTRAP_CONFIG to config)

    private fun testObtainEntityManagerFactory(
        dbConfig: SmartConfig, entityManagerFactoryFactory: EntityManagerFactoryFactory,
        entitiesSet: EntitiesSet
    ): EntityManagerFactory {
        Triple(
            dbConfig,
            entityManagerFactoryFactory,
            entitiesSet
        )
        return mock()
    }

    @Test
    fun `processing a stop event stops the permission storage writer`() {
        handler.processEvent(StartEvent(), mock())
        assertNull(handler.subscription)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())
        handler.onConfigurationUpdated(setOf(ConfigKeys.BOOTSTRAP_CONFIG), bootstrapConfig)

        assertNotNull(handler.subscription)
        verify(subscription).start()
        handler.processEvent(StopEvent(), mock())
        assertNull(handler.subscription)
        verify(subscription).stop()
    }
}