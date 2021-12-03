package net.corda.virtualnode.common

interface InstanceIdSupplier {
    fun get() : Int?
}
