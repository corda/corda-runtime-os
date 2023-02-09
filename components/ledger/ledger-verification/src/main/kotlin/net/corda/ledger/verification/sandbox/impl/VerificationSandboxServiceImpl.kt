package net.corda.ledger.verification.sandbox.impl

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
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun get(holdingIdentity: HoldingIdentity, cpks: List<CordaPackageSummary>): SandboxGroupContext {

        val cpkFileChecksums = cpks.mapTo(mutableSetOf()) { SecureHash.parse(it.fileChecksum) }
        if (!sandboxService.hasCpks(cpkFileChecksums)) {
            throw CpkNotAvailableException(
                "CPKs for Verification Sandbox for $holdingIdentity are not available (yet): $cpks"
            )
        }

        val sandbox = sandboxService.getOrCreate(getVirtualNodeContext(holdingIdentity, cpkFileChecksums)) { _, ctx ->
            initializeSandbox(holdingIdentity, ctx)
        }

        // Only contract CPKs are allowed
        val nonContractCpks = sandbox.sandboxGroup
            .metadata
            .values
            .filterNot { it.isContractCpk() }

        if (nonContractCpks.isNotEmpty()) {
            val cpkIds = nonContractCpks.map { it.cpkId }
            throw NotAllowedCpkException(
                "CPKs for Verification Sandbox for $holdingIdentity are not allowed: $cpkIds"
            )
        }

        return sandbox
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

fun SandboxGroupContext.getSerializationService(): SerializationService =
    getObjectByKey(RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE)
        ?: throw CordaRuntimeException(
            "Verification serialization service not found within the sandbox for identity: " +
                    "${virtualNodeContext.holdingIdentity}"
        )
