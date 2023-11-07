package net.corda.entityprocessor.impl.internal

import net.corda.sandboxgroupcontext.SandboxGroupContext


fun SandboxGroupContext.getClass(fullyQualifiedClassName: String) =
    this.sandboxGroup.loadClassFromMainBundles(fullyQualifiedClassName)

