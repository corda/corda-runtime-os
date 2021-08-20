@file:Suppress("unused")
package com.example.sandbox.cpk3

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.Flow
import org.osgi.framework.Bundle
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(name = "bundles.three.flow")
class BundlesThreeFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : Flow<List<Bundle>> {
    override fun call(): List<Bundle> {
        return sandboxQuery.getAllBundles()
    }
}
