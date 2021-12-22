package com.example.sandbox.library

import com.example.sandbox.library.impl.WrapperImpl

/** A simple wrapper class for testing purposes. */
interface Wrapper {
    val data: String
}

/** A factory to retrieve [WrapperImpl] from its non-exported package. */
class WrapperFactory {
    companion object {
        /** Returns a [WrapperImpl] instance. */
        fun create(): Wrapper = WrapperImpl()
    }
}