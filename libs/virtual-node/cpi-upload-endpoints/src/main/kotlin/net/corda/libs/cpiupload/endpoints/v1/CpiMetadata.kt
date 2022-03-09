package net.corda.libs.cpiupload.endpoints.v1

data class CpiMetadata(
    val id : CpiIdentifier,
    val fileChecksum : String,
    val cpks : List<CpkMetadata>,
    val groupPolicy : String?)