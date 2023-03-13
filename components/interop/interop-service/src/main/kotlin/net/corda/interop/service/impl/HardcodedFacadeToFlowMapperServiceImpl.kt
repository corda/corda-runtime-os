package net.corda.interop.service.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.interop.data.FacadeFlowMapping
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory


@Component(service = [InteropFacadeToFlowMapperService::class])
class HardcodedFacadeToFlowMapperServiceImpl @Activate constructor() : InteropFacadeToFlowMapperService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val mapper = jacksonObjectMapper()
    }

    override fun getFlowName(
        destinationIdentity: HoldingIdentity,
        facadeId: String,
        facadeName: String
    ): String? {
        logger.info("Fetching flow name for holding identity : ${destinationIdentity.x500Name} " +
                    "facade id : $facadeId facade name : $facadeName")
        val facadeFlowMapping = readFacadeToFlowMapping(destinationIdentity, facadeId, facadeName)
        checkNotNull(facadeFlowMapping) { "Failed to find the facade to flow mapping " +
                "for holding identity : ${destinationIdentity.x500Name} facade id : $facadeId facade name : $facadeName" }

        val facadeInfo = facadeFlowMapping.facadeFlowMapping.firstOrNull { it.facadeId == facadeId }
        checkNotNull(facadeInfo) { "Failed to find the facade to flow mapping for facadeId : $facadeId" }

        val flowMapping = facadeInfo.facadeMethodMapping.firstOrNull { it.facadeMethod == facadeName }
        checkNotNull(flowMapping) { "Failed to find the facade to flow mapping for facadeName : $facadeName" }

        return flowMapping.flowName
    }

    private fun readFacadeToFlowMapping(
        destinationIdentity: HoldingIdentity,
        facadeId: String,
        facadeName: String
    ): FacadeFlowMapping? = try {
        // TODO : Now we are using hardcoded facade definition and it will be replaced with real one in next milestone
        val content = this::class.java.getResource("/dummy-facade-to-flow-config.json")?.readText().toString()
        mapper.readValue(content, FacadeFlowMapping::class.java)
    } catch (e: Exception) {
        throw IllegalStateException(
            "Error while parsing the facade to flow mapping for holding identity : ${destinationIdentity.x500Name} " +
                    "facade id : $facadeId facade name : $facadeName :  $e"
        )
    }
}