package net.corda.sandboxgroupcontext

import net.corda.packaging.CPK

interface SandboxGroupContextService {
    /**
     * This function creates and returns a "fully constructed" node that is ready to
     * execute a flow with no further configuration required.  Your supplied initializer will be run
     * during the [getOrCreate] call.
     *
     * A mutable interface is passed so that the initializer can add per CPI custom objects that are
     * cached together with their SandboxGroup.  The initializer must return an `AutoCloseable` object.
     *
     * The lifetime of the [SandboxGroupContext] will be managed by the [SandboxGroupContextService]
     * implementation subject to some lifetime policy (e.g. resource pressure).
     *
     * Your initializer should be idempotent across a process, and also across different processes running on the same
     * system, i.e. for a given set of identical inputs, the constructed objects should be identical in terms of data.
     *
     * This function will be implemented as a pure function, i.e. if the "virtual node" already exists an
     * is constructed, that should be returned, and the [initializer] will not be run on subsequent calls
     * of [getOrCreate].
     *
     * Initializers (functional interface [SandboxGroupContextInitializer]) are expected to be:
     *
     *     fun initialize(holdingIdentity: HoldingIdentity, ctx: MutableSandboxGroupContext) : AutoCloseable {
     *       val someObject = SomeObject() // : AutoCloseable
     *       val otherObject = OtherObject() // : AutoCloseable
     *       val anotherObject = AnotherObject() // NOT AutoCloseable
     *       ctx.putObject(someKey, someObject)
     *       ctx.putObject(otherObjectKey, otherObject)
     *       ctx.putUniqueObject(anotherObject)
     *       return object : AutoCloseable {
     *         override fun close() {
     *           someObject.close()
     *           otherObject.close()
     *         }
     *       }
     *     }
     *
     *     private var ctx : SandboxGroupContext? = null
     *
     *     fun setUp() {
     *       if (! service.hasCpks(cpkIdentifiers)) { /* some retry logic */ }
     *       ctx = service.getOrCreate(VirtualNodeContext(holdingIdentity, cpkIdentifiers, type), ::initialize)
     *       val someObject = ctx!!.getObject<SomeObject>(someKey)
     *       val anotherObject = ctx!!.getUniqueObject<AnotherObject>()
     *       someObject.doThing()
     *       anotherObject.doSomethingElse()
     *    }
     *
     *    fun doSomethingElse(virtualNodeContext: VirtualNodeContext) {
     *       ctx!!.getObject<SomeObject>(someKey)!!.doThing()
     *       ctx!!.getUniqueObject<AnotherObject>()!!.doSomethingElse()
     *    }
     *
     * @throws Exception or "something" if the requested [CPK]s cannot be found in the local package cache.
     *
     * @return a non-null [SandboxGroupContext] instance
     */
    fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext

    /**
     * Does the service 'contains' the cpks in its cache?
     *
     *     if (service.hasCpks(virtualNodeContext.cpkIdentifiers)) {
     *        service.getOrCreate(virtualNodeContext) { .... }
     *     }
     */
    fun hasCpks(cpkIdentifiers: Set<CPK.Identifier>) : Boolean
}
