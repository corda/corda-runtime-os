package net.corda.flow.pipeline.sandbox

import net.corda.sandboxgroupcontext.service.SandboxDependencyInjector
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.Flow

interface FlowSandboxDependencyInjector : SandboxDependencyInjector<Flow>, NonSerializable