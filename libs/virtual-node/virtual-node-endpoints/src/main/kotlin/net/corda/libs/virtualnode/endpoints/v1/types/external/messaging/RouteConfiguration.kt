package net.corda.libs.virtualnode.endpoints.v1.types.external.messaging

import com.fasterxml.jackson.annotation.JsonProperty

data class RouteConfiguration(
    @JsonProperty("currentRoutes")
    val currentRoutes: Routes,
    @JsonProperty("previousVersionRoutes")
    val previousVersionRoutes: List<Routes>
)
