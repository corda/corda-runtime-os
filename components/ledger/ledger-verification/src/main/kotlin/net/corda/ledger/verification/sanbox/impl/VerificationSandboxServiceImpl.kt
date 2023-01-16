package net.corda.ledger.verification.sanbox.impl

import net.corda.ledger.verification.sanbox.VerificationSandboxService
import net.corda.ledger.verification.exceptions.NotReadyException
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.RequireSandboxJSON
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.service.registerCordappCustomSerializers
import net.corda.sandboxgroupcontext.service.registerCustomCryptography
import net.corda.sandboxgroupcontext.service.registerCustomJsonDeserializers
import net.corda.sandboxgroupcontext.service.registerCustomJsonSerializers
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This is a sandbox service that is internal to this component.
 *
 * It gets/creates a sandbox with a per-sandbox:
 *
 *   * serializer
 *   * entity manager factory
 *
 */
@Suppress("LongParameterList")
@RequireSandboxAMQP
@RequireSandboxJSON
@Component(service = [ VerificationSandboxService::class ])
class VerificationSandboxServiceImpl @Activate constructor(
    @Reference
    private val sandboxService: SandboxGroupContextComponent,
) : VerificationSandboxService {
    companion object {
        private val logger = contextLogger()
    }

    override fun get(holdingIdentity: HoldingIdentity, cpkFileChecksums: Set<SecureHash>): SandboxGroupContext {
        // We're throwing internal exceptions so that we can relay some information back to the flow worker
        // on how to proceed with any request to us that fails.
        if (!sandboxService.hasCpks(cpkFileChecksums))
            throw NotReadyException("CPKs not available (yet): $cpkFileChecksums")

        return sandboxService.getOrCreate(getVirtualNodeContext(holdingIdentity, cpkFileChecksums)) { _, ctx ->
            initializeSandbox(holdingIdentity, ctx)
        }
    }

    private fun initializeSandbox(
        holdingIdentity: HoldingIdentity,
        ctx: MutableSandboxGroupContext
    ): AutoCloseable {
        val customCrypto = sandboxService.registerCustomCryptography(ctx)
        val customSerializers = sandboxService.registerCordappCustomSerializers(ctx)
        val jsonDeserializers = sandboxService.registerCustomJsonDeserializers(ctx)
        val jsonSerializers = sandboxService.registerCustomJsonSerializers(ctx)

        // Instruct all CustomMetadataConsumers to accept their metadata.
        sandboxService.acceptCustomMetadata(ctx)

        logger.info("Initialising Verification Sandbox for $holdingIdentity")

        return AutoCloseable {
            logger.info("Closing Verification Sandbox for $holdingIdentity")
            jsonSerializers.close()
            jsonDeserializers.close()
            customSerializers.close()
            customCrypto.close()
        }
    }

    /** NOTE THE SANDBOX GROUP TYPE HERE */
    private fun getVirtualNodeContext(holdingIdentity: HoldingIdentity, cpkFileChecksums: Set<SecureHash>) =
        VirtualNodeContext(
            holdingIdentity,
            cpkFileChecksums,
            SandboxGroupType.VERIFICATION,
            null
        )
}
