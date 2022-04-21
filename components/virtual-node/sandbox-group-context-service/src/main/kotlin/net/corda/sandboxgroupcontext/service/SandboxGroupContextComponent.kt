package net.corda.sandboxgroupcontext.service

import net.corda.lifecycle.Lifecycle
import net.corda.sandboxgroupcontext.SandboxGroupContextService

interface SandboxGroupContextComponent : SandboxGroupContextService, Lifecycle {
    fun initCache(cacheSize: Long)
}
