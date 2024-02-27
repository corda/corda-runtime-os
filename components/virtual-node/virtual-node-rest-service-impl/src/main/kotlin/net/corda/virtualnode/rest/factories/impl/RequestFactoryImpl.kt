package net.corda.virtualnode.rest.factories.impl

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeDbConnectionUpdateRequest
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.UpdateVirtualNodeDbRequest
import net.corda.rest.security.RestContextProvider
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.rest.factories.RequestFactory
import net.corda.virtualnode.toAvro
import java.util.*

internal class RequestFactoryImpl(
    private val restContextProvider: RestContextProvider,
    private val clock: Clock
) : RequestFactory {

    override fun createHoldingIdentity(groupId: String, request: CreateVirtualNodeRequest): HoldingIdentity {
        return HoldingIdentity(MemberX500Name.parse(request.x500Name), groupId)
    }

    override fun createVirtualNodeRequest(
        holdingIdentity: HoldingIdentity,
        request: CreateVirtualNodeRequest
    ): VirtualNodeAsynchronousRequest {
        return VirtualNodeAsynchronousRequest().apply {
            this.requestId = holdingIdentity.shortHash.toString()
            this.timestamp = clock.instant()
            this.request = VirtualNodeCreateRequest().apply {
                this.holdingId = holdingIdentity.toAvro()
                this.cpiFileChecksum = request.cpiFileChecksum
                this.vaultDdlConnection = request.vaultDdlConnection
                this.vaultDmlConnection = request.vaultDmlConnection
                this.cryptoDdlConnection = request.cryptoDdlConnection
                this.cryptoDmlConnection = request.cryptoDmlConnection
                this.uniquenessDdlConnection = request.uniquenessDdlConnection
                this.uniquenessDmlConnection = request.uniquenessDmlConnection
                this.updateActor = restContextProvider.principal
            }
        }
    }

    override fun updateVirtualNodeDbRequest(
        holdingIdentity: HoldingIdentity,
        request: UpdateVirtualNodeDbRequest
    ): VirtualNodeAsynchronousRequest {
        return VirtualNodeAsynchronousRequest().apply {
            this.requestId = UUID.randomUUID().toString()
            this.timestamp = clock.instant()
            this.request = VirtualNodeDbConnectionUpdateRequest().apply {
                this.holdingId = holdingIdentity.toAvro()
                this.vaultDdlConnection = request.vaultDdlConnection?.escapedJson
                this.vaultDmlConnection = request.vaultDmlConnection?.escapedJson
                this.cryptoDdlConnection = request.cryptoDdlConnection?.escapedJson
                this.cryptoDmlConnection = request.cryptoDmlConnection?.escapedJson
                this.uniquenessDdlConnection = request.uniquenessDdlConnection?.escapedJson
                this.uniquenessDmlConnection = request.uniquenessDmlConnection?.escapedJson
                this.updateActor = restContextProvider.principal
            }
        }
    }
}
