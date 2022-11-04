package net.corda.sandboxgroupcontext

fun interface CustomMetadataConsumer {
    fun accept(context: MutableSandboxGroupContext)
}
