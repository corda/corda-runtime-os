package net.corda.libs.cpi.datamodel

import java.util.*

data class CpkDbChangeLogAudit(val id: String = UUID.randomUUID().toString(), val changeLog: CpkDbChangeLog)
