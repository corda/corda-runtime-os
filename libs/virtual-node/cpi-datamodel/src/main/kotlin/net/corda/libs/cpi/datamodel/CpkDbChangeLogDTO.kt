package net.corda.libs.cpi.datamodel

class CpkDbChangeLogDTO(
    val id: CpkDbChangeLogKey,
    val cpkFileChecksum: String,
    val content: String
)