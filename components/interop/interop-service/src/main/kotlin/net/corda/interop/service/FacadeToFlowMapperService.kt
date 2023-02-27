package net.corda.interop.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.interop.data.FacadeFlowMapping
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [FacadeToFlowMapperService::class])
class FacadeToFlowMapperService @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService
) {
    companion object {
        private const val FACADE_TO_FLOW_MAPPING = "FACADE_TO_FLOW_MAPPING"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())
    }

    fun getFlowName(
        destinationIdentity: HoldingIdentity,
        facadeId: String,
        facadeName: String
    ): String {
        logger.info(
            "Fetching flow name for holding identity : ${destinationIdentity.x500Name} " +
                    "facade id : $facadeId facade name : $facadeName"
        )
        // Now we are using hardcoded facade definition and
        // the assumption is we will get the required facade to flow mapping configuration as a part of CPIMetaData.
        // The following code snippet suggests how we can read it and when it will be available.
        val vNodeInfo = virtualNodeInfoReadService.get(destinationIdentity)
        var content: String
        if (vNodeInfo != null) {
            checkNotNull(vNodeInfo) {
                "Failed to find the virtual node info for holder '${destinationIdentity}' in ${virtualNodeInfoReadService::class.java}"
            }
            val cpiMetadata = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
            checkNotNull(cpiMetadata) { "Failed to find the CPI meta data for '${vNodeInfo.cpiIdentifier}}'" }
            check(cpiMetadata.cpksMetadata.isNotEmpty()) { "No CPKs defined for CPI Meta data id='${cpiMetadata.cpiId}'" }
            // In below code we are assuming that we will read the content from first CPK Metadata
            // If the packaging team will change the solution in the future, then we need make appropriate changes.
            content = cpiMetadata.cpksMetadata.first().cordappManifest.attributes[FACADE_TO_FLOW_MAPPING]?:""
        } else {
            content = this::class.java.getResource("/dummy-facade-to-flow-config.yaml")?.readText().toString()
        }

        if (content.trim().isEmpty()) {
            throw IllegalStateException("Failed to fetch facade to flow mapping.")
        } else {
            return mapper.readValue(
                content,
                FacadeFlowMapping::class.java
            ).facadeFlowMapping
                .first { it.facadeId == facadeId }.facadeMethodMapping
                .first { it.facadeMethod == facadeName }
                .flowName
        }
    }
}