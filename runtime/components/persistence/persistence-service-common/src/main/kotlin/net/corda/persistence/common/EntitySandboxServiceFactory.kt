package net.corda.persistence.common

import net.corda.cpk.read.CpkReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.persistence.common.internal.EntitySandboxServiceImpl
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.virtualnode.read.VirtualNodeInfoReadService

class EntitySandboxServiceFactory {
    fun create(
        sandboxService: SandboxGroupContextComponent,
        cpkReadService: CpkReadService,
        virtualNodeInfoService: VirtualNodeInfoReadService,
        dbConnectionManager: DbConnectionManager
    ) : EntitySandboxService =
        EntitySandboxServiceImpl(
            sandboxService,
            cpkReadService,
            virtualNodeInfoService,
            dbConnectionManager)
}
