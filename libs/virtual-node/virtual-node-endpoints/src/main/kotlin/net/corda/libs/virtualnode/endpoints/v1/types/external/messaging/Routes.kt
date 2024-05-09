package net.corda.libs.virtualnode.endpoints.v1.types.external.messaging

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier

data class Routes(
    val cpiIdentifier: CpiIdentifier,
    val routes: List<Route>
)
