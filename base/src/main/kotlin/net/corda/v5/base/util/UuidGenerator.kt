package net.corda.v5.base.util

import java.util.*

class UuidGenerator {

    companion object {
        fun next(): UUID = UUID.randomUUID()
    }
}