package net.corda.virtualnode.sandboxgroup

import net.corda.packaging.CPI
import net.corda.virtualnode.HoldingIdentity

interface SandboxGroupService {
    /**
     * This function returns a "fully constructed" node that is ready to
     * execute a flow with no further configuration required.
     *
     * Implicitly maps the [HoldingIdentity] to only ONE [CPI]
     *
     * A mutable interface is passed so that the initializer can add per CPI custom objects that are
     * cached together with their SandboxGroup.  The initializer must return an `AutoCloseable` object.
     *
     * This function will be implemented as a pure function, i.e. if the "virtual node" already exists and
     * is constructed, that should be returned, and the [initializer] should not be run.
     *
     * Initializers are expected to be:
     *
     *     fun initialize(holdingIdentity: HoldingIdentity, ctx: MutableSandboxGroupContext) : AutoCloseable {
     *       val someObject = SomeObject() // : AutoCloseable
     *       val otherObject = OtherObject() // : AutoCloseable
     *       val anotherObject = AnotherObject() // NOT AutoCloseable
     *       ctx.put(someKey, someObject)
     *       ctx.put(otherObjectKey, otherObject)
     *       ctx.put(anotherObjectKey, anotherObject)
     *       return object : AutoCloseable {
     *         override fun close() {
     *           someObject.close()
     *           otherObject.close()
     *         }
     *     }
     *
     *   Usage:
     *
     *     fun doSomething() {
     *       val ctx = service.get(holdingIdentity, cpi, type, ::initialize)
     *       val someObject = ctx.get<SomeObject>(someKey)
     *       someObject.doThing()
     *    }
     */
    fun get(
        key: VirtualNodeContext,
        initializer: (holdingIdentity: HoldingIdentity, sandboxGroupContext: MutableSandboxGroupContext) -> AutoCloseable
    ): SandboxGroupContext
}
