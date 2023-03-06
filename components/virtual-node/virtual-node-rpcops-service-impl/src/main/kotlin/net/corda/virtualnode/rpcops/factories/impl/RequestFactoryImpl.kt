package net.corda.virtualnode.rpcops.factories.impl

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.rest.security.RestContextProvider
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.rpcops.factories.RequestFactory
import net.corda.virtualnode.toAvro

internal class RequestFactoryImpl(
    private val restContextProvider: RestContextProvider,
    private val clock: Clock
) : RequestFactory {

    override fun createHoldingIdentity(groupId: String, request: CreateVirtualNodeRequest): HoldingIdentity{
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
}