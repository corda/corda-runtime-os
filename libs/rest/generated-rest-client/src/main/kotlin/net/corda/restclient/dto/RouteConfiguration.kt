package net.corda.restclient.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This is a duplicate of [net.corda.libs.external.messaging.entities.RouteConfiguration]
 * Because we need to use the custom [Routes] class
 */
data class RouteConfiguration(
    @JsonProperty("currentRoutes")
    val currentRoutes: Routes,
    @JsonProperty("previousVersionRoutes")
    val previousVersionRoutes: List<Routes>
)
