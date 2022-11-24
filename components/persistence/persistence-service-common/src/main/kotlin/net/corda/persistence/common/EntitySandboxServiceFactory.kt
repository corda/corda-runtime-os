package net.corda.persistence.common

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.persistence.common.internal.EntitySandboxServiceImpl
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.virtualnode.read.VirtualNodeInfoReadService

class EntitySandboxServiceFactory {
    fun create(
        sandboxService: SandboxGroupContextComponent,
        cpiInfoService: CpiInfoReadService,
        virtualNodeInfoService: VirtualNodeInfoReadService,
        dbConnectionManager: DbConnectionManager
    ) : EntitySandboxService =
        EntitySandboxServiceImpl(
            sandboxService,
            cpiInfoService,
            virtualNodeInfoService,
            dbConnectionManager)
}
