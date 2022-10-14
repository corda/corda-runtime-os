package net.corda.persistence.common

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.persistence.common.internal.EntitySandboxServiceImpl
import net.corda.sandboxgroupcontext.service.SandboxGroupComponent
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.ComponentContext

class EntitySandboxServiceFactory {
    fun create(
        sandboxService: SandboxGroupComponent,
        cpiInfoService: CpiInfoReadService,
        virtualNodeInfoService: VirtualNodeInfoReadService,
        dbConnectionManager: DbConnectionManager,
        componentContext: ComponentContext
    ) : EntitySandboxService =
        EntitySandboxServiceImpl(
            sandboxService,
            cpiInfoService,
            virtualNodeInfoService,
            dbConnectionManager,
            componentContext)
}