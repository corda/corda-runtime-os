package net.corda.db.persistence.testkit.components

import net.corda.persistence.common.EntitySandboxService
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ SandboxService::class ])
class SandboxService  @Activate constructor(
    @Reference
    val entitySandboxService: EntitySandboxService,

    @Reference
    val sandboxGroupContextComponent: SandboxGroupContextComponent
) {
    init {
        sandboxGroupContextComponent.initCache(2)
    }
}
