package net.corda.libs.virtualnode.maintenance.rest.impl.v1

internal class UnknownMaintenanceResponseTypeException(type: String) : Exception("Encountered a response of unknown type: $type")
