package net.corda.httprpc.test

import net.corda.httprpc.PluggableRPCOps

class CustomSerializationAPIImpl : CustomSerializationAPI, PluggableRPCOps<CustomSerializationAPI> {
    override val targetInterface: Class<CustomSerializationAPI>
        get() = CustomSerializationAPI::class.java

    override val protocolVersion: Int
        get() = 2

    override fun printString(s: CustomString): CustomString {
        return s
    }

    override fun printUnsafeString(s: CustomUnsafeString): CustomUnsafeString {
        return s
    }

    override fun printCustomMarshalString(s: CustomMarshalString): CustomMarshalString {
        return s
    }
}