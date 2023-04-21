package com.example.shadow

import com.esotericsoftware.reflectasm.FieldAccess
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable

@Suppress("unused")
class ShadowFlow : SubFlow<String> {
    @Suspendable
    override fun call(): String {
        val fieldAccess: FieldAccess = FieldAccess.get(this::class.java)
        return fieldAccess.fieldNames.joinToString()
    }
}
