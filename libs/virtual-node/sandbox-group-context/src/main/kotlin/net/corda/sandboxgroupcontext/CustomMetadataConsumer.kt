package net.corda.sandboxgroupcontext

interface CustomMetadataConsumer {
    fun accept(context: MutableSandboxGroupContext)
}
