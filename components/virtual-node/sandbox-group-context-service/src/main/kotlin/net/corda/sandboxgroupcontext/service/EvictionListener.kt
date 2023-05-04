package net.corda.sandboxgroupcontext.service

import net.corda.sandboxgroupcontext.VirtualNodeContext

fun interface EvictionListener {
    fun onEviction(vnc: VirtualNodeContext)
}
