package net.corda.sandboxgroupcontext

/**
 * All objects that are [put] into the context are held by their `(type, key)` tuple.
 *
 * An instance of this interface is returned to the initializer via [SandboxGroupContextService].
 *
 * Attempts to [put] another object with the same `(type,key)` throws an [IllegalArgumentException]
 *
 * We make no guarantees about the implementation of the interface other than if you use
 * [net.corda.sandboxgroupcontext.MutableSandboxGroupContext.put],
 * the object can be retrieved by [net.corda.sandboxgroupcontext.SandboxGroupContext.get]
 */
interface MutableSandboxGroupContext : SandboxGroupContext {
    /**
     * Put an object into the cache.
     *
     * IMPORTANT:  you probably want to use the extension methods instead [putObjectByKey] and [putUniqueObject]
     *
     * Instances of this interface are only passed into [SandboxGroupContextInitializer]
     *
     * If the object you [put] is [AutoCloseable] you MUST call [AutoCloseable.close] on your object(s) as part of the
     * [AutoCloseable] that you return from [SandboxGroupContextInitializer], i.e.
     *
     *     override fun initializeSandboxGroupContext(holdingIdentity: HoldingIdentity, ctx: MutableSandboxGroupContext) : AutoCloseable {
     *       val someObject = SomeObject() // : AutoCloseable
     *       val anotherObject = AnotherObject() // NOT AutoCloseable
     *       ctx.putUniqueObject(someObject)
     *       ctx.putUniqueObject(anotherObject)
     *       return object : AutoCloseable {
     *         override fun close() {
     *           someObject.close()
     *         }
     *     }
     *
     *     // Note:  immutable AutoCloseable interface here.
     *     private var ctx : SandboxGroupContext? = null
     *
     *     fun setUp() {
     *       ctx = service.getOrCreate(VirtualNodeContext(holdingIdentity, cpi, type), ::initialize)
     *       val someObject = ctx!!.getObject<SomeObject>(someKey)
     *       val anotherObject = ctx!!.getUniqueObject<AnotherObject>()
     *       someObject.doThing()
     *       anotherObject.doSomethingElse()
     *    }
     *
     * @throws IllegalArgumentException if any attempts to [put] another object with the same type
     */
    fun <T : Any> put(key: String, valueType: Class<out T>, value: T)
}
