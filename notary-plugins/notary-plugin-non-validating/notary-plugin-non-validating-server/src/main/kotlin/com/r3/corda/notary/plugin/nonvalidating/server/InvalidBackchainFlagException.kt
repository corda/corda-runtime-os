package com.r3.corda.notary.plugin.nonvalidating.server

class InvalidBackchainFlagException : IllegalArgumentException("Non-validating notary can't switch backchain verification off.")