package net.corda.bundle4

import net.corda.bundle2.Document
import net.corda.bundle3.Obligation

class Transfer(val obligation: Obligation, val document: Document) {

    /**
     * This toString method uses the Document class from TestSerializable 2.
     */
    override fun toString() ="with (amount: ${obligation}, content: ${document.content}"
}