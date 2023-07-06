package net.corda.flow.pipeline.sandbox.impl

import net.corda.flow.pipeline.sandbox.FlowSandboxDependencyInjector
import net.corda.sandboxgroupcontext.service.SandboxDependencyInjector
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.Flow

/**
 * [SandboxDependencyInjector] for Flow only.
 * This is due to [NonSerializable] in sandbox injector
 * and to avoid to amend this dependency in any other modules.
 */
class FlowSandboxDependencyInjectorImpl(private val delegate: SandboxDependencyInjector<Flow>) :
    SandboxDependencyInjector<Flow> by delegate, FlowSandboxDependencyInjector, NonSerializable