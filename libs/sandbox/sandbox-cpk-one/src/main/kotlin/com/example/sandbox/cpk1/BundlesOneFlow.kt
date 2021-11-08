package com.example.sandbox.cpk1

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.Flow
import org.osgi.framework.Bundle
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** Returns [Bundle]s visible to this sandbox. */
@Suppress("unused")
@Component(name = "bundles.one.flow")
class BundlesOneFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : Flow<List<Bundle>> {
    override fun call() = sandboxQuery.getAllBundles()
}
