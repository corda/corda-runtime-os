package net.corda.dependency.injection

import net.corda.virtual.node.sandboxgroup.SandboxGroupContext

interface DependencyInjectionBuilder {
    fun addSandboxDependencies(sandboxGroupContext: SandboxGroupContext)
    fun build(): FlowDependencyInjector

}