package net.corda.virtualnode.write.db.impl.tests.writer

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ExternalMessagingConfig
import net.corda.schema.configuration.VirtualNodeDatasourceConfig
import net.corda.virtualnode.write.db.impl.writer.CLIENT_NAME_DB
import net.corda.virtualnode.write.db.impl.writer.CLIENT_NAME_RPC
import net.corda.virtualnode.write.db.impl.writer.GROUP_NAME
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationProcessor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.persistence.EntityManagerFactory

/** Tests of [VirtualNodeWriterFactory]. */
class VirtualNodeWriterFactoryTests {

    companion object {
        private const val BOOT_CONFIG_STRING = """${ConfigKeys.TEMP_DIR} = "dir.tmp""""
    }

    private val configFactory = SmartConfigFactory.createWithoutSecurityServices()

    /** Returns a mock [SubscriptionFactory]. */
    private fun getSubscriptionFactory() = mock<SubscriptionFactory>().apply {
        whenever(createRPCSubscription<Any, Any>(any(), any(), any())).doReturn(mock())
        whenever(
            createDurableSubscription<String, VirtualNodeAsynchronousRequest>(
                any(),
                any(),
                any(),
                eq(null)
            )
        ).thenReturn(mock())
    }

    private fun genExternalMessagingConfig() =
        configFactory.create(
            ConfigFactory.empty()
                .withValue(
                    ExternalMessagingConfig.EXTERNAL_MESSAGING_RECEIVE_TOPIC_PATTERN,
                    ConfigValueFactory.fromAnyRef("ext.\$HOLDING_ID.\$CHANNEL_NAME.receive")
                )
                .withValue(ExternalMessagingConfig.EXTERNAL_MESSAGING_ACTIVE, ConfigValueFactory.fromAnyRef(true))
                .withValue(
                    ExternalMessagingConfig.EXTERNAL_MESSAGING_INTERACTIVE_RESPONSE_TYPE,
                    ConfigValueFactory.fromAnyRef(InactiveResponseType.IGNORE.toString())
                )
        )

    private fun genVnodeDatasourceConfig() =
        configFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "${VirtualNodeDatasourceConfig.VNODE_DDL_POOL_CONFIG}.dummy" to "",
                    "${VirtualNodeDatasourceConfig.VNODE_DML_POOL_CONFIG}.dummy" to ""
                )
            )
        )

    /** Returns a mock [PublisherFactory]. */
    private fun getPublisherFactory() = mock<PublisherFactory>().apply {
        whenever(createPublisher(any(), any())).thenReturn(mock())
    }

    /** Returns a mock [DbConnectionManager]. */
    private fun getDbConnectionManager() = mock<DbConnectionManager>().apply {
        whenever(clusterConfig).thenReturn(mock<SmartConfig>())
        whenever(getClusterEntityManagerFactory()).thenReturn(mock<EntityManagerFactory>())
    }

    @Test
    fun `factory creates a publisher with the correct configuration`() {
        val expectedPublisherConfig = PublisherConfig(CLIENT_NAME_DB)
        val expectedConfig = configFactory.create(ConfigFactory.parseMap(mapOf("dummyKey" to "dummyValue")))
        val externalMsgConfig = genExternalMessagingConfig()
        val vnodeDatasourceConfig = genVnodeDatasourceConfig()
        val bootConfig = configFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))

        val publisherFactory = getPublisherFactory()
        val virtualNodeWriterFactory = VirtualNodeWriterFactory(
            getSubscriptionFactory(),
            publisherFactory,
            getDbConnectionManager(),
            mock(), mock(), mock(), mock(), mock(), mock(), mock(),
            CpiCpkRepositoryFactory(),
            mock(),
            mock(),
        )
        virtualNodeWriterFactory.create(expectedConfig, externalMsgConfig, vnodeDatasourceConfig, bootConfig)

        verify(publisherFactory).createPublisher(expectedPublisherConfig, expectedConfig)
    }

    @Test
    fun `factory creates subscriptions with the correct configuration`() {
        val expectedRPCConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeManagementRequest::class.java,
            VirtualNodeManagementResponse::class.java,
        )
        val subscriptionConfig = SubscriptionConfig(
            "virtual.node.async.operation.group",
            Schemas.VirtualNode.VIRTUAL_NODE_ASYNC_REQUEST_TOPIC
        )
        val expectedConfig = configFactory.create(ConfigFactory.parseMap(mapOf("dummyKey" to "dummyValue")))
        val externalMsgConfig = genExternalMessagingConfig()
        val vnodeDatasourceConfig = genVnodeDatasourceConfig()
        val bootConfig = configFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))

        val subscriptionFactory = getSubscriptionFactory()
        val virtualNodeWriterFactory = VirtualNodeWriterFactory(
            subscriptionFactory,
            getPublisherFactory(),
            getDbConnectionManager(),
            mock(), mock(), mock(), mock(), mock(), mock(), mock(),
            CpiCpkRepositoryFactory(),
            mock(),
            mock(),
        )

        val processor = argumentCaptor<VirtualNodeAsyncOperationProcessor>()
        virtualNodeWriterFactory.create(expectedConfig, externalMsgConfig, vnodeDatasourceConfig, bootConfig)

        verify(subscriptionFactory).createRPCSubscription(eq(expectedRPCConfig), eq(expectedConfig), any())
        verify(subscriptionFactory).createDurableSubscription(
            eq(subscriptionConfig),
            processor.capture(),
            eq(expectedConfig),
            eq(null)
        )
    }
}
