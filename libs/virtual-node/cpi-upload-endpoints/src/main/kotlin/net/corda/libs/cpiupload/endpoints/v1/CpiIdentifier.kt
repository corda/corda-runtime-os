package net.corda.libs.cpiupload.endpoints.v1

data class CpiIdentifier(val cpiName: String, val cpiVersion: String, val signerSummaryHash: String?)