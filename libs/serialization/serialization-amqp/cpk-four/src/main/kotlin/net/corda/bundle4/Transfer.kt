package net.corda.bundle4

import net.corda.bundle5.Container
import net.corda.bundle2.Document
import net.corda.bundle3.Obligation
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class Transfer(val obligation: Obligation, val document: Document, val id: Container<Int>) {

    /**
     * This toString method uses the Document class from TestSerializable 2.
     */
    override fun toString() ="with (amount: ${obligation}, content: ${document.content}, id: ${id.obj})"
}