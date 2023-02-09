package net.corda.ledger.verification.sandbox.impl

import net.corda.cpk.read.CpkReadService
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.NotAllowedCpkException
import net.corda.ledger.utxo.verification.CordaPackageSummary
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.RequireSandboxJSON
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.service.registerCordappCustomSerializers
import net.corda.sandboxgroupcontext.service.registerCustomCryptography
import net.corda.sandboxgroupcontext.service.registerCustomJsonDeserializers
import net.corda.sandboxgroupcontext.service.registerCustomJsonSerializers
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * This is a sandbox service that is internal to this component.
 *
 * It gets/creates a Verification sandbox with a per-sandbox serializers.
 *
 */
@RequireSandboxAMQP
@RequireSandboxJSON
@Component(service = [ VerificationSandboxService::class ])
class VerificationSandboxServiceImpl @Activate constructor(
    @Reference
    private val sandboxService: SandboxGroupContextComponent,
    @Reference
    private val cpkReadService: CpkReadService
) : VerificationSandboxService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun get(holdingIdentity: HoldingIdentity, cpks: List<CordaPackageSummary>): SandboxGroupContext {
        checkCpks(holdingIdentity, cpks)
        return sandboxService.getOrCreate(getVirtualNodeContext(holdingIdentity, cpks)) { _, ctx ->
            initializeSandbox(holdingIdentity, ctx)
        }
    }

    private fun checkCpks(holdingIdentity: HoldingIdentity, cpks: List<CordaPackageSummary>) {
        cpks.forEach {
            val cpk = cpkReadService.get(it.fileChecksum.toSecureHash()) ?: throw CpkNotAvailableException(
                "CPK for Verification Sandbox for $holdingIdentity is not available (yet): $it"
            )
            if (!cpk.metadata.isContractCpk()) {
                throw NotAllowedCpkException(
                    "CPK for Verification Sandbox for $holdingIdentity is not allowed: $it"
                )
            }
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
    private fun getVirtualNodeContext(
        holdingIdentity: HoldingIdentity,
        cpks: List<CordaPackageSummary>
    ): VirtualNodeContext {
        val cpkFileChecksums = cpks.mapTo(mutableSetOf()) { it.fileChecksum.toSecureHash() }
        return VirtualNodeContext(
            holdingIdentity,
            cpkFileChecksums,
            SandboxGroupType.VERIFICATION,
            null
        )
    }

    private fun String.toSecureHash() = SecureHash.parse(this)
}

fun SandboxGroupContext.getSerializationService(): SerializationService =
    getObjectByKey(RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE)
        ?: throw CordaRuntimeException(
            "Verification serialization service not found within the sandbox for identity: " +
                    "${virtualNodeContext.holdingIdentity}"
        )
