package net.corda.interop.service.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("UNCHECKED_CAST")
@Component(service = [InteropFacadeToFlowMapperService::class])
class FacadeToFlowMapperServiceImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService
) : InteropFacadeToFlowMapperService {
    companion object {
        private const val FACADE_TO_FLOW_MAPPING = "FACADE_TO_FLOW_MAPPING"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val mapper = jacksonObjectMapper()
    }

    override fun getFlowName(
        destinationIdentity: HoldingIdentity,
        facadeId: String,
        facadeName: String
    ): String? {
        logger.info(
            "Fetching flow name for holding identity : ${destinationIdentity.x500Name} " +
                    "facade id : $facadeId facade name : $facadeName"
        )
        // Now we are using hardcoded facade definition and
        // the assumption is we will get the required facade to flow mapping configuration as a part of CPIMetaData.
        // The following code snippet suggests how we can read it and when it will be available.
        val vNodeInfo = virtualNodeInfoReadService.get(destinationIdentity)
        val content = if (vNodeInfo != null) {
            checkNotNull(vNodeInfo) {
                "Failed to find the virtual node info for holder '${destinationIdentity}' in ${virtualNodeInfoReadService::class.java}"
            }
            val cpiMetadata = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
            checkNotNull(cpiMetadata) { "Failed to find the CPI meta data for '${vNodeInfo.cpiIdentifier}}'" }
            check(cpiMetadata.cpksMetadata.isNotEmpty()) { "No CPKs defined for CPI Meta data id='${cpiMetadata.cpiId}'" }
            // In below code we are assuming that we will read the content from first CPK Metadata
            // If the packaging team will change the solution in the future, then we need make appropriate changes.
            cpiMetadata.cpksMetadata.first().cordappManifest.attributes[FACADE_TO_FLOW_MAPPING] ?: ""
        } else {
            this::class.java.getResource("/dummy-facade-to-flow-config.json")?.readText().toString()
        }

        if (content.trim().isEmpty()) {
            throw IllegalStateException("Failed to fetch facade to flow mapping.")
        } else {
            val facadeFlowMapping = getFacadeMapping(content)
            checkNotNull(facadeFlowMapping) { "Failed to find the facade to flow mapping" }
            val facadeIdMap = facadeFlowMapping[facadeId]
            checkNotNull(facadeIdMap) { "Failed to find the facade to flow mapping for facadeId : $facadeId" }
            val flowName = (facadeIdMap as MutableMap<String, String>)[facadeName]
            checkNotNull(flowName) { "Failed to find the facade to flow mapping for facadeName : $facadeName" }
            return flowName.toString()
        }
    }

    private fun getFacadeMapping(content: String): MutableMap<*, *> = try {
        mapper.readValue(content, MutableMap::class.java)
    } catch (e: Exception) {
        throw IllegalStateException("Unable to parse the facade to flow mapping : $e")
    }
}