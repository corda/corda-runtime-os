package net.corda.flow.pipeline.handlers.waiting.sessions

const val PROTOCOL_MISMATCH_HINT =
    "Please check CorDapp protocol expectations match on both sides regarding 'send' and 'receive' statements and that " +
            "sessions are not closed unexpectedly on either side. Stack traces around this error originating in CorDapp " +
            "code may provide a hint."
