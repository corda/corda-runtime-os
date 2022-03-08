package net.corda.libs.cpiupload.endpoints.v1

data class CpkIdentifier(val name : String, val version : String, val signerSummaryHash : String?)
