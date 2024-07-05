package net.corda.ledger.lib.impl.stub.sandbox

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import org.osgi.framework.Bundle
import java.util.*

class StubSandboxGroup : SandboxGroup {

    override fun getEvolvableTag(klass: Class<*>): String {
        return "Whatever"
    }

    override val id: UUID
        get() = TODO("Not yet implemented")
    override val metadata: Map<Bundle, CpkMetadata>
        get() = TODO("Not yet implemented")

    override fun loadClassFromPublicBundles(className: String): Class<*>? {
        TODO("Not yet implemented")
    }

    override fun loadClassFromMainBundles(className: String): Class<*> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> {
        TODO("Not yet implemented")
    }

    override fun getStaticTag(klass: Class<*>): String {
        TODO("Not yet implemented")
    }

    override fun getClass(className: String, serialisedClassTag: String): Class<*> {
        TODO("Not yet implemented")
    }
}
