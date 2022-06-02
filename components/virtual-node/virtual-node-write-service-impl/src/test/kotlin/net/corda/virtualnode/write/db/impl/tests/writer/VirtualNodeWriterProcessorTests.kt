package net.corda.virtualnode.write.db.impl.tests.writer

import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHash
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.packaging.CpiIdentifier
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.GroupPolicyParser
import net.corda.membership.impl.MGMContextImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.converter.EndpointInfoConverter
import net.corda.membership.impl.converter.PublicKeyConverter
import net.corda.membership.impl.converter.PublicKeyHashConverter
import net.corda.membership.impl.toMemberInfo
import net.corda.membership.impl.toSortedMap
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.CPIMetadata
import net.corda.virtualnode.write.db.impl.writer.DbConnection
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbType
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterProcessor
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

/** Tests of [VirtualNodeWriterProcessor]. */
class VirtualNodeWriterProcessorTests {
    companion object {
        const val CPI_ID_SHORT_HASH = "dummy_cpi_id_short_hash"
    }

    private val groupId = "dummy_mgm_group_id"
    private val x500Name = "OU=LLC, O=Bob, L=Dublin, C=IE"
    private val holdingIdentity = HoldingIdentity(x500Name, groupId)
    private val mgmHoldingIdentity = HoldingIdentity(
        "CN=Corda Network MGM, OU=MGM, O=Corda Network, L=London, C=GB",
        "f3676687-ab69-4ca1-a17b-ab20b7bc6d03"
    )

    private val secureHash = SecureHash(
        "SHA-256",
        ByteBuffer.wrap("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray())
    )
    private val cpiIdentifier = CpiIdentifier("dummy_name", "dummy_version", secureHash)
    val summaryHash = net.corda.v5.crypto.SecureHash.create("SHA-256:0000000000000000")
    private val cpiId = net.corda.libs.packaging.core.CpiIdentifier("dummy_name", "dummy_version", summaryHash)
    private val cpiMetaData =
        CPIMetadata(cpiId, CPI_ID_SHORT_HASH, groupId, "{\"groupId\": \"${UUID.randomUUID()}\"}")
    private val cpiMetaDataWithMGM =
        CPIMetadata(cpiId, CPI_ID_SHORT_HASH, groupId, getSampleGroupPolicy())
    private val connectionId = UUID.randomUUID().toString()
    private val vnodeInfo =
        VirtualNodeInfo(
            holdingIdentity.toAvro(),
            cpiIdentifier,
            connectionId, connectionId, connectionId, connectionId,
            null)

    private val vnodeCreationReq =
        VirtualNodeCreationRequest(vnodeInfo.holdingIdentity.x500Name, CPI_ID_SHORT_HASH,
            "dummy_vault_ddl_config", "dummy_vault_dml_config",
            "dummy_crypto_ddl_config", "dummy_crypto_dml_config", "update_actor")

    private val em = mock<EntityManager>() {
        on { transaction }.doReturn(mock<EntityTransaction>())
    }

    private val emf = mock<EntityManagerFactory>() {
        on { createEntityManager() }.doReturn(em)
    }

    private val dataSource = mock<CloseableDataSource>() {
        on { connection }.doReturn(mock<Connection>())
    }

    private val connectionManager = mock<DbConnectionManager>() {
        on { getDataSource(any()) }.doReturn(dataSource)
        on { getClusterEntityManagerFactory() }.doReturn(emf)
        on { putConnection(any(), any(), any(), any(), any(), any()) }.doReturn(UUID.fromString(connectionId))
    }

    private val dbConnection = mock<DbConnection>() {
        on { name }.doReturn("connection")
        on { privilege }.doReturn(DbPrivilege.DDL)
        on { config }.doReturn(mock<SmartConfig>())
        on { description }.doReturn("description")
        on { getUser() }.doReturn("user")
        on { getPassword() }.doReturn("password")
    }

    private val dbConnections = mapOf(
        DbPrivilege.DDL to dbConnection,
        DbPrivilege.DML to dbConnection,
    )

    private val vNodeFactory = mock<VirtualNodeDbFactory>() {
        on { createVNodeDbs(any(), any()) }.doReturn(mapOf(
            VirtualNodeDbType.VAULT to VirtualNodeDb(
                VirtualNodeDbType.VAULT, true, "holdingIdentityId", dbConnections, mock(), connectionManager, mock()),
            VirtualNodeDbType.CRYPTO to VirtualNodeDb(
                VirtualNodeDbType.CRYPTO, true, "holdingIdentityId", dbConnections, mock(), connectionManager, mock())
        ))
    }

    private val vNodeRepo = mock<VirtualNodeEntityRepository>() {
        on { getCPIMetadata(any()) }.doReturn(cpiMetaData)
        on { virtualNodeExists(any(), any()) }.doReturn(false)
        on { getHoldingIdentity(any()) }.doReturn(null)
    }

    private val defaultKey: PublicKey = mock {
        on { encoded } doReturn "1234".toByteArray()
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(any<String>()) } doReturn defaultKey
    }
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(
        listOf(EndpointInfoConverter(), PublicKeyConverter(keyEncodingService), PublicKeyHashConverter())
    )
    private val groupPolicyParser =  GroupPolicyParser(keyEncodingService, layeredPropertyMapFactory)

    private val publisherError = CordaMessageAPIIntermittentException("Error.")

    /** Returns a mock [Publisher]. */
    private fun getPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    /** Returns a mock [Publisher] that throws an error whenever it tries to publish. */
    private fun getErroringPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(
            listOf(CompletableFuture.supplyAsync { throw publisherError })
        )
    }

    /** Calls [processor].`onNext` for the given [req], and returns the result of the future. */
    private fun processRequest(
        processor: VirtualNodeWriterProcessor,
        req: VirtualNodeCreationRequest
    ): VirtualNodeCreationResponse {

        val respFuture = CompletableFuture<VirtualNodeCreationResponse>()
        processor.onNext(req, respFuture)
        return respFuture.get()
    }

    @Test
    fun `publishes correct virtual node info to Kafka`() {
        val expectedRecord = Record(VIRTUAL_NODE_INFO_TOPIC, vnodeInfo.holdingIdentity, vnodeInfo)

        val publisher = getPublisher()
        val processor = VirtualNodeWriterProcessor(
            publisher,
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
        )

        processRequest(processor, vnodeCreationReq)

        verify(publisher).publish(listOf(expectedRecord))
    }

    @Test
    fun `publishes MGM member info to Kafka`() {
        val publisher = getPublisher()
        val vNodeRepo = mock<VirtualNodeEntityRepository>() {
            on { getCPIMetadata(any()) }.doReturn(cpiMetaDataWithMGM)
        }
        val processor = VirtualNodeWriterProcessor(
            publisher,
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
        )
        processRequest(processor, vnodeCreationReq)

        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        // called twice for publishing virtual node info and MGM info
        verify(publisher, times(2)).publish(capturedPublishedList.capture())
        val publishedVirtualNodeList = capturedPublishedList.firstValue
        assertSoftly {
            it.assertThat(publishedVirtualNodeList.size).isEqualTo(1)
            val publishedVirtualNode = publishedVirtualNodeList.first()
            it.assertThat(publishedVirtualNode.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)
            it.assertThat((publishedVirtualNode.value as VirtualNodeInfo).holdingIdentity.toCorda())
                .isEqualTo(holdingIdentity)
        }
        val publishedMgmInfoList = capturedPublishedList.secondValue
        assertSoftly {
            it.assertThat(publishedMgmInfoList.size).isEqualTo(1)
            val publishedMgmInfo = publishedMgmInfoList.first()
            it.assertThat(publishedMgmInfo.topic).isEqualTo(Schemas.Membership.MEMBER_LIST_TOPIC)
            val expectedRecordKey = "${holdingIdentity.id}-${mgmHoldingIdentity.id}"
            it.assertThat(publishedMgmInfo.key).isEqualTo(expectedRecordKey)
            val persistentMemberPublished = publishedMgmInfo.value as PersistentMemberInfo
            val mgmPublished = toMemberInfo(
                layeredPropertyMapFactory.create<MemberContextImpl>(
                    KeyValuePairList.fromByteBuffer(persistentMemberPublished.memberContext).toSortedMap()
                ),
                layeredPropertyMapFactory.create<MGMContextImpl>(
                    KeyValuePairList.fromByteBuffer(persistentMemberPublished.mgmContext).toSortedMap()
                )
            )
            it.assertThat(mgmPublished.name.toString())
                .isEqualTo("CN=Corda Network MGM, OU=MGM, O=Corda Network, L=London, C=GB")
        }
    }

    @Test
    fun `skips MGM member info publishing to Kafka without error if MGM information is not present in group policy`() {
        val publisher = getPublisher()
        val vNodeRepo = mock<VirtualNodeEntityRepository>() {
            on { getCPIMetadata(any()) }.doReturn(cpiMetaData)
        }
        val processor = VirtualNodeWriterProcessor(
            publisher,
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
        )
        processRequest(processor, vnodeCreationReq)

        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        // called once for publishing virtual node info
        verify(publisher, times(1)).publish(capturedPublishedList.capture())
        val publishedVirtualNodeList = capturedPublishedList.firstValue
        assertSoftly {
            it.assertThat(publishedVirtualNodeList.size).isEqualTo(1)
            val publishedVirtualNode = publishedVirtualNodeList.first()
            it.assertThat(publishedVirtualNode.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)
            it.assertThat((publishedVirtualNode.value as VirtualNodeInfo).holdingIdentity.toCorda())
                .isEqualTo(holdingIdentity)
        }
    }

    @Test
    fun `sends RPC success response after publishing virtual node info to Kafka`() {
        val expectedResp = VirtualNodeCreationResponse(
            true,
            null,
            vnodeCreationReq.x500Name,
            vnodeInfo.cpiIdentifier,
            vnodeCreationReq.cpiFileChecksum,
            vnodeInfo.holdingIdentity.groupId,
            vnodeInfo.holdingIdentity,
            holdingIdentity.id,
            connectionId,
            connectionId,
            connectionId,
            connectionId,
            null
        )

        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
        )
        val resp = processRequest(processor, vnodeCreationReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if fails to publish virtual node info to Kafka`() {
        val expectedEnvelope = ExceptionEnvelope(
            VirtualNodeWriteServiceException::class.java.name,
            ""
        )
        val expectedResp = VirtualNodeCreationResponse(
            false,
            expectedEnvelope,
            vnodeCreationReq.x500Name,
            vnodeInfo.cpiIdentifier,
            vnodeCreationReq.cpiFileChecksum,
            vnodeInfo.holdingIdentity.groupId,
            vnodeInfo.holdingIdentity,
            holdingIdentity.id,
            null,
            null,
            null,
            null,
            null
        )

        val processor = VirtualNodeWriterProcessor(
            getErroringPublisher(),
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
        )
        val resp = processRequest(processor, vnodeCreationReq)

        assertEquals(expectedResp.success, resp.success)
        assertEquals(expectedResp.x500Name, resp.x500Name)
        assertEquals(expectedResp.cpiIdentifier, resp.cpiIdentifier)
        assertEquals(expectedResp.cpiFileChecksum, resp.cpiFileChecksum)
        assertEquals(expectedResp.mgmGroupId, resp.mgmGroupId)
        assertEquals(expectedResp.holdingIdentity, resp.holdingIdentity)
        assertEquals(expectedResp.holdingIdentifierHash, resp.holdingIdentifierHash)
        assertEquals(expectedEnvelope.errorType, resp.exception.errorType)
        assertTrue(resp.exception.errorMessage.contains("written to the database, but couldn't be published"))
    }

    @Test
    fun `sends RPC failure response if the CPI with the given ID is not stored on the node`() {
        val expectedEnvelope = ExceptionEnvelope(
            VirtualNodeWriteServiceException::class.java.name,
            "CPI with file checksum ${vnodeCreationReq.cpiFileChecksum} was not found."
        )
        val expectedResp = VirtualNodeCreationResponse(
            false, expectedEnvelope, x500Name , null, null, null, null, null,
            null, null, null, null, null)

        val entityRepository = mock<VirtualNodeEntityRepository>().apply {
            whenever(getCPIMetadata(any())).thenReturn(null)
        }
        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            entityRepository,
            vNodeFactory,
            groupPolicyParser,
        )
        val resp = processRequest(processor, vnodeCreationReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if the there is a holding-identity collision`() {
        val expectedEnvelope = ExceptionEnvelope(
            VirtualNodeWriteServiceException::class.java.name,
            ""
        )

        val expectedResp = VirtualNodeCreationResponse(
            false,
            expectedEnvelope,
            vnodeCreationReq.x500Name,
            vnodeInfo.cpiIdentifier,
            vnodeCreationReq.cpiFileChecksum,
            vnodeInfo.holdingIdentity.groupId,
            vnodeInfo.holdingIdentity,
            holdingIdentity.id,
            null,
            null,
            null,
            null,
            null
        )

        val collisionHoldingIdentity = mock<HoldingIdentity>() {
            on { x500Name }.thenReturn("OU=LLC, O=Alice, L=Dublin, C=IE")
            on { groupId }.thenReturn("group_id")
            on { id }.thenReturn(holdingIdentity.id)
        }

        val entityRepository = mock<VirtualNodeEntityRepository>() {
            on { getCPIMetadata(any()) }.doReturn(cpiMetaData)
            on { virtualNodeExists(any(), any()) }.doReturn(false)
            on { getHoldingIdentity(any()) }.doReturn(collisionHoldingIdentity)
        }

        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            entityRepository,
            vNodeFactory,
            groupPolicyParser,
        )
        val resp = processRequest(processor, vnodeCreationReq)

        assertEquals(expectedResp.success, resp.success)
        assertEquals(expectedResp.x500Name, resp.x500Name)
        assertEquals(expectedResp.cpiIdentifier, resp.cpiIdentifier)
        assertEquals(expectedResp.cpiFileChecksum, resp.cpiFileChecksum)
        assertEquals(expectedResp.mgmGroupId, resp.mgmGroupId)
        assertEquals(expectedResp.holdingIdentity, resp.holdingIdentity)
        assertEquals(expectedResp.holdingIdentifierHash, resp.holdingIdentifierHash)
        assertEquals(expectedEnvelope.errorType, resp.exception.errorType)
        assertTrue(resp.exception.errorMessage.contains(
            "New holding identity $holdingIdentity has a short hash that collided with existing holding identity"))
    }

    private fun getSampleGroupPolicy() = this::class.java.getResource("/SampleGroupPolicy.json")!!.readText()
}