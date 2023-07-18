package net.corda.libs.cpi.datamodel.repository.factory

import net.corda.libs.cpi.datamodel.repository.CpiCpkRepository
import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogAuditRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.cpi.datamodel.repository.CpkFileRepository
import net.corda.libs.cpi.datamodel.repository.CpkRepository
import net.corda.libs.cpi.datamodel.repository.impl.CpiCpkRepositoryImpl
import net.corda.libs.cpi.datamodel.repository.impl.CpiMetadataRepositoryImpl
import net.corda.libs.cpi.datamodel.repository.impl.CpkDbChangeLogAuditRepositoryImpl
import net.corda.libs.cpi.datamodel.repository.impl.CpkDbChangeLogRepositoryImpl
import net.corda.libs.cpi.datamodel.repository.impl.CpkFileRepositoryImpl
import net.corda.libs.cpi.datamodel.repository.impl.CpkRepositoryImpl

class CpiCpkRepositoryFactory {
    fun createCpiCpkRepository(): CpiCpkRepository =
        CpiCpkRepositoryImpl()

    fun createCpiMetadataRepository(): CpiMetadataRepository =
        CpiMetadataRepositoryImpl()

    fun createCpkDbChangeLogAuditRepository(): CpkDbChangeLogAuditRepository =
        CpkDbChangeLogAuditRepositoryImpl()

    fun createCpkDbChangeLogRepository(): CpkDbChangeLogRepository =
        CpkDbChangeLogRepositoryImpl()

    fun createCpkFileRepository(): CpkFileRepository =
        CpkFileRepositoryImpl()

    fun createCpkRepository(): CpkRepository =
        CpkRepositoryImpl()
}
