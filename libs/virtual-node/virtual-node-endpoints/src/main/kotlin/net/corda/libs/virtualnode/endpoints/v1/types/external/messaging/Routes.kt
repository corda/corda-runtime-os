package net.corda.libs.virtualnode.endpoints.v1.types.external.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier


data class Routes(
    @JsonProperty("cpiIdentifier")
    val cpiIdentifier: CpiIdentifier,
    @JsonProperty("routes")
    val routes: List<Route>
)
