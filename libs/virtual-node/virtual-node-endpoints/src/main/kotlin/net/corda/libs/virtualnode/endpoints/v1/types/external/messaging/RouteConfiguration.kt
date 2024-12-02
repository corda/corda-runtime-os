package net.corda.libs.virtualnode.endpoints.v1.types.external.messaging

data class RouteConfiguration(
    val currentRoutes: Routes,
    val previousVersionRoutes: List<Routes>
)
