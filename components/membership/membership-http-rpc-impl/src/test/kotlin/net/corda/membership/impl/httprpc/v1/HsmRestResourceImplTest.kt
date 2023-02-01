package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts.Categories.CI
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.NOTARY
import net.corda.crypto.core.CryptoConsts.Categories.TLS
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.CryptoTenants.REST_API
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.rest.v1.types.response.HsmAssociationInfo
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HsmRestResourceImplTest {
    private val hsmRegistrationClient = mock<HSMRegistrationClient>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val tenantId = "000000000000"
    private val tenantIdShortHash = ShortHash.of((tenantId))
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(tenantIdShortHash) } doReturn mock()
    }

    private val ops = HsmRestResourceImpl(hsmRegistrationClient, lifecycleCoordinatorFactory, virtualNodeInfoReadService)

    @Nested
    inner class ApiTests {
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
        fun `assignedHsm verify the tenantId`() {
            ops.assignedHsm(tenantId, "Notary")

            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(tenantIdShortHash)
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
                HSMAssociationInfo(
                    "id3",
                    tenantId,
                    "hsm-id",
                    TLS,
                    "master-key-alias",
                    0,
                )
            )

            val hsm = ops.assignedHsm(tenantId, "tls")

            assertThat(hsm).isEqualTo(
                HsmAssociationInfo(
                    id = "id3",
                    hsmId = "hsm-id",
                    category = TLS,
                    masterKeyAlias = "master-key-alias",
                    deprecatedAt = 0
                )
            )
        }

        @Test
        fun `assignSoftHsm will return the data`() {
            whenever(hsmRegistrationClient.assignSoftHSM(tenantId, CI)).doReturn(
                HSMAssociationInfo(
                    "id1",
                    tenantId,
                    "SOFT",
                    CI,
                    "master-key-alias",
                    0
                ),
            )

            val hsm = ops.assignSoftHsm(tenantId, "ci")

            assertThat(hsm).isEqualTo(
                HsmAssociationInfo(
                    id = "id1",
                    category = CI,
                    hsmId = "SOFT",
                    masterKeyAlias = "master-key-alias",
                    deprecatedAt = 0
                ),
            )
        }

        @Test
        fun `assignHsm will return the data`() {
            whenever(hsmRegistrationClient.assignHSM(tenantId, LEDGER, emptyMap())).doReturn(
                HSMAssociationInfo(
                    "id1",
                    tenantId,
                    "hsm-id",
                    LEDGER,
                    "master-key-alias",
                    0,
                )
            )

            val hsm = ops.assignHsm(tenantId, LEDGER)

            assertThat(hsm).isEqualTo(
                HsmAssociationInfo(
                    id = "id1",
                    hsmId = "hsm-id",
                    category = LEDGER,
                    masterKeyAlias = "master-key-alias",
                    deprecatedAt = 0
                ),
            )
        }

        @Test
        fun `assignHsm verify the tenantId`() {
            whenever(hsmRegistrationClient.assignHSM(tenantId, LEDGER, emptyMap())).doReturn(
                HSMAssociationInfo(
                    "id1",
                    tenantId,
                    "hsm-id",
                    LEDGER,
                    "master-key-alias",
                    0,
                )
            )

            ops.assignHsm(tenantId, LEDGER)

            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(tenantIdShortHash)
        }

        @Test
        fun `assignHsm will not verify the tenantId from RPC tenant`() {
            whenever(hsmRegistrationClient.assignHSM(REST_API, LEDGER, emptyMap())).doReturn(
                HSMAssociationInfo(
                    "id1",
                    REST_API,
                    "hsm-id",
                    LEDGER,
                    "master-key-alias",
                    0,
                )
            )

            ops.assignHsm(REST_API, LEDGER)

            verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(tenantIdShortHash)
        }

        @Test
        fun `assignSoftHsm verify the tenantId`() {
            whenever(hsmRegistrationClient.assignSoftHSM(tenantId, CI)).doReturn(
                HSMAssociationInfo(
                    "id1",
                    tenantId,
                    "SOFT",
                    CI,
                    "master-key-alias",
                    0
                ),
            )

            ops.assignSoftHsm(tenantId, "ci")

            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(tenantIdShortHash)
        }

        @Test
        fun `assignSoftHsm will not verify the tenantId for p2p tenant`() {
            whenever(hsmRegistrationClient.assignSoftHSM(P2P, CI)).doReturn(
                HSMAssociationInfo(
                    "id1",
                    P2P,
                    "SOFT",
                    CI,
                    "master-key-alias",
                    0
                ),
            )

            ops.assignSoftHsm(P2P, "ci")

            verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(tenantIdShortHash)
        }

        @Test
        fun `assignedHsm will throw resource not found exception for unknown tenant ID`() {
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(tenantIdShortHash)).doReturn(null)

            assertThrows<ResourceNotFoundException> {
                ops.assignedHsm(tenantId, "Notary")
            }
        }

        @Test
        fun `assignedHsm will throw bad input exception for invalid tenant ID`() {
            assertThrows<BadRequestException> {
                ops.assignedHsm("12AB$", "Notary")
            }
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
