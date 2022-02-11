package net.corda.example.vnode

import java.nio.file.Paths
import net.corda.testing.sandboxes.SandboxSetup
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component(service = [], immediate = true)
class SandboxBootstrap @Activate constructor(
    @Reference
    private val sandboxSetup: SandboxSetup,
    bundleContext: BundleContext
){
    init {
        val baseDirectory = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath()
        sandboxSetup.configure(bundleContext, baseDirectory)
    }
}
