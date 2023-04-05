package net.corda.libs.external.messaging.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class RouteConfiguration(
    @JsonProperty("currentRoutes")
    val currentRoutes: Routes,
    @JsonProperty("previousVersionRoutes")
    val previousVersionRoutes: List<Routes>
)
