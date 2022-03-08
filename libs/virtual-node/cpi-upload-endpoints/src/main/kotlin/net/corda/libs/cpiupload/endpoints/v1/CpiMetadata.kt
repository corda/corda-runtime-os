package net.corda.libs.cpiupload.endpoints.v1

data class CpiMetadata(
    val id : CpiIdentifier,
    val hash : String,
    val cpks : List<CpkMetadata>,
    val groupPolicy : String?)