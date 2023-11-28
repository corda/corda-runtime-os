package com.r3.corda.testing.bundle4

import com.r3.corda.testing.bundle2.Document
import com.r3.corda.testing.bundle3.Obligation
import com.r3.corda.testing.bundle5.Container
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class Transfer(val obligation: Obligation, val document: Document, val id: Container<Int>) {

    /**
     * This toString method uses the Document class from TestSerializable 2.
     */
    override fun toString() = "with (amount: $obligation, content: ${document.content}, id: ${id.obj})"
}
