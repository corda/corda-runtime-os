package net.corda.sandboxgroupcontext.service

import net.corda.lifecycle.Lifecycle
import net.corda.sandboxgroupcontext.SandboxGroupService

interface SandboxGroupComponent : SandboxGroupService, CacheConfiguration, Lifecycle

