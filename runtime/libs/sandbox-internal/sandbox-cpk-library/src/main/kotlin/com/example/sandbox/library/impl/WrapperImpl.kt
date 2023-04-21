package com.example.sandbox.library.impl

import com.example.sandbox.library.Wrapper

/** A [Wrapper] implementation in a non-exported package. */
class WrapperImpl : Wrapper {
    override val data = "String returned by WrapperImpl."
}