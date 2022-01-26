package net.corda.cpi.upload.endpoints.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.virtualnode.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.HTTPCpiUploadRequestId
import org.osgi.service.component.annotations.Component

@Component(service = [PluggableRPCOps::class], immediate = true)
class CpiUploadRPCOpsImpl : CpiUploadRPCOps, PluggableRPCOps<CpiUploadRPCOps> {

    override val protocolVersion: Int = 1

    override val targetInterface: Class<CpiUploadRPCOps> = CpiUploadRPCOps::class.java

    override fun cpi(file: ByteArray): HTTPCpiUploadRequestId {
        // TODO - fix dummy return value
        return HTTPCpiUploadRequestId(5)
    }
}