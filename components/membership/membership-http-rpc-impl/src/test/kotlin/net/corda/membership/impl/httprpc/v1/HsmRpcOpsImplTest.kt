package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.HSMConfigurationClient
import net.corda.crypto.client.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts.Categories.CI
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.NOTARY
import net.corda.crypto.core.CryptoConsts.Categories.TLS
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.httprpc.v1.types.response.HsmInfo
import net.corda.membership.httprpc.v1.types.response.New
import net.corda.membership.httprpc.v1.types.response.None
import net.corda.membership.httprpc.v1.types.response.Shared
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class HsmRpcOpsImplTest {
    private val hsmConfigurationClient = mock<HSMConfigurationClient>()
    private val hsmRegistrationClient = mock<HSMRegistrationClient>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val tenantId = "id"

    private val ops = HsmRpcOpsImpl(hsmConfigurationClient, hsmRegistrationClient, lifecycleCoordinatorFactory)

    @Nested
    inner class ApiTests {
        @Test
        fun `listHsms return the correct structure`() {
            val hsms = listOf(
                HSMInfo(
                    "id1",
                    Instant.ofEpochMilli(100),
                    "label 1",
                    "description 1",
                    MasterKeyPolicy.NONE,
                    null,
                    5,
                    800L,
                    listOf("schema1", "schema2"),
                    "service A",
                    -1,
                ),
                HSMInfo(
                    "id2",
                    Instant.ofEpochMilli(1000),
                    "label 2",
                    "description 2",
                    MasterKeyPolicy.SHARED,
                    "a1",
                    25,
                    100L,
                    listOf("schema3"),
                    "service B",
                    2,
                ),
                HSMInfo(
                    "id3",
                    Instant.ofEpochMilli(3000),
                    "label 3",
                    "description 3",
                    MasterKeyPolicy.NEW,
                    null,
                    100,
                    300L,
                    emptyList(),
                    "service C",
                    3,
                ),
            )
            whenever(hsmConfigurationClient.lookup(emptyMap())).doReturn(hsms)

            val reply = ops.listHsms()

            assertThat(reply)
                .containsExactlyInAnyOrder(
                    HsmInfo(
                        id = "id1",
                        createdAt = Instant.ofEpochMilli(100),
                        workerLabel = "label 1",
                        description = "description 1",
                        retries = 5,
                        timeout = Duration.ofMillis(800L),
                        masterKeyPolicy = None,
                        capacity = null,
                        serviceName = "service A",
                        supportedSchemes = listOf("schema1", "schema2"),
                    ),
                    HsmInfo(
                        id = "id2",
                        createdAt = Instant.ofEpochMilli(1000),
                        workerLabel = "label 2",
                        description = "description 2",
                        retries = 25,
                        timeout = Duration.ofMillis(100L),
                        masterKeyPolicy = Shared("a1"),
                        capacity = 2,
                        serviceName = "service B",
                        supportedSchemes = listOf("schema3"),
                    ),
                    HsmInfo(
                        id = "id3",
                        createdAt = Instant.ofEpochMilli(3000),
                        workerLabel = "label 3",
                        description = "description 3",
                        retries = 100,
                        timeout = Duration.ofMillis(300L),
                        masterKeyPolicy = New,
                        capacity = 3,
                        serviceName = "service C",
                        supportedSchemes = emptyList(),
                    ),
                )
        }

        @Test
        fun `assignedHsm returns null if no HSM found`() {
            whenever(hsmRegistrationClient.findHSM(tenantId, TLS)).doReturn(null)

            val hsm = ops.assignedHsm(tenantId, "tls")

            assertThat(hsm).isNull()
        }

        @Test
        fun `assignedHsm calls the client with upper case`() {
            ops.assignedHsm(tenantId, "Notary")

            verify(hsmRegistrationClient).findHSM(tenantId, NOTARY)
        }

        @Test
        fun `assignedHsm throws exception for unexpected category`() {
            assertThrows<ResourceNotFoundException> {
                ops.assignedHsm(tenantId, "Notary category")
            }
        }


        @Test
        fun `assignedHsm returns the correct value`() {
            whenever(hsmRegistrationClient.findHSM(tenantId, TLS)).doReturn(
                HSMInfo(
                    "id3",
                    Instant.ofEpochMilli(3000),
                    "label 3",
                    "description 3",
                    MasterKeyPolicy.NEW,
                    null,
                    100,
                    300L,
                    emptyList(),
                    "service C",
                    3,
                )
            )

            val hsm = ops.assignedHsm(tenantId, "tls")

            assertThat(hsm).isEqualTo(
                HsmInfo(
                    id = "id3",
                    createdAt = Instant.ofEpochMilli(3000),
                    workerLabel = "label 3",
                    description = "description 3",
                    retries = 100,
                    timeout = Duration.ofMillis(300L),
                    masterKeyPolicy = New,
                    capacity = 3,
                    serviceName = "service C",
                    supportedSchemes = emptyList(),
                )
            )
        }

        @Test
        fun `assignSoftHsm will return the data`() {
            whenever(hsmRegistrationClient.assignSoftHSM(tenantId, CI, emptyMap())).doReturn(
                HSMInfo(
                    "id1",
                    Instant.ofEpochMilli(100),
                    "label 1",
                    "description 1",
                    MasterKeyPolicy.NONE,
                    null,
                    5,
                    800L,
                    listOf("schema1", "schema2"),
                    "service A",
                    -1,
                ),
            )

            val hsm = ops.assignSoftHsm(tenantId, "ci")

            assertThat(hsm).isEqualTo(
                HsmInfo(
                    id = "id1",
                    createdAt = Instant.ofEpochMilli(100),
                    workerLabel = "label 1",
                    description = "description 1",
                    retries = 5,
                    timeout = Duration.ofMillis(800L),
                    masterKeyPolicy = None,
                    capacity = null,
                    serviceName = "service A",
                    supportedSchemes = listOf("schema1", "schema2"),
                ),
            )
        }

        @Test
        fun `assignHsm will return the data`() {
            whenever(hsmRegistrationClient.assignHSM(tenantId, LEDGER, emptyMap())).doReturn(
                HSMInfo(
                    "id1",
                    Instant.ofEpochMilli(100),
                    "label 1",
                    "description 1",
                    MasterKeyPolicy.NONE,
                    null,
                    5,
                    800L,
                    listOf("schema1", "schema2"),
                    "service A",
                    -1,
                ),
            )

            val hsm = ops.assignHsm(tenantId, LEDGER)

            assertThat(hsm).isEqualTo(
                HsmInfo(
                    id = "id1",
                    createdAt = Instant.ofEpochMilli(100),
                    workerLabel = "label 1",
                    description = "description 1",
                    retries = 5,
                    timeout = Duration.ofMillis(800L),
                    masterKeyPolicy = None,
                    capacity = null,
                    serviceName = "service A",
                    supportedSchemes = listOf("schema1", "schema2"),
                ),
            )
        }
    }

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `isRunning returns the coordinator status`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            assertThat(ops.isRunning).isTrue
        }

        @Test
        fun `start starts the coordinator`() {
            ops.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop stops the coordinator`() {
            ops.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `UP event will set the status to up`() {
            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            verify(coordinator).updateStatus(LifecycleStatus.UP, "Dependencies are UP")
        }

        @Test
        fun `DOWN event will set the status to down`() {
            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), mock())

            verify(coordinator).updateStatus(LifecycleStatus.DOWN, "Dependencies are DOWN")
        }
    }
}
/*
    override fun assignSoftHsm(tenantId: String, category: String) = hsmRegistrationClient.assignSoftHSM(
        tenantId, category.toCategory(), emptyMap()
    ).expose()

    override fun assignHsm(tenantId: String, category: String) = hsmRegistrationClient.assignHSM(
        tenantId, category.toCategory(), emptyMap()
    ).expose()


 */
