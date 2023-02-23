package net.corda.libs.cpi.datamodel

data class CpkDbChangeLog(
    val id: CpkDbChangeLogIdentifier,
    val content: String
)
