package net.corda.sandboxgroupcontext

import net.corda.virtualnode.HoldingIdentity

/**
 *  Functional interface to allow us to configure a new sandbox group context when we create it.
 *
 *  You must close any [AutoCloseable] objects that you put into the cache in the returned object
 *  if they are not 'singleton' type objects shared across multiple [VirtualNodeContext] instances
 *  or the whole process.
 *
 *     override fun initializeSandboxGroupContext(holdingIdentity: HoldingIdentity, ctx: MutableSandboxGroupContext) : AutoCloseable {
 *       val someObject = SomeObject() // : AutoCloseable
 *       val otherObject = OtherObject() // : AutoCloseable
 *       val anotherObject = AnotherObject() // NOT AutoCloseable
 *       ctx.putObject(someKey, someObject)
 *       ctx.putUniqueObject(otherObject)
 *       ctx.putUniqueObject(anotherObject)
 *       return object : AutoCloseable {
 *         override fun close() {
 *           someObject.close()
 *           otherObject.close()
 *         }
 *     }
 *
 *  @return [AutoCloseable] object that closes any other [AutoCloseable] objects placed into the cache.
 */
fun interface SandboxGroupContextInitializer {
    fun initializeSandboxGroupContext(
        holdingIdentity: HoldingIdentity,
        mutableSandboxGroupContext: MutableSandboxGroupContext
    ): AutoCloseable
}
