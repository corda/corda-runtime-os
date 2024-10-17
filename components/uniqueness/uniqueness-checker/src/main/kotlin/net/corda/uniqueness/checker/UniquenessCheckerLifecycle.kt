package net.corda.uniqueness.checker

import net.corda.ledger.libs.uniqueness.UniquenessChecker
import net.corda.lifecycle.Lifecycle

interface UniquenessCheckerLifecycle : UniquenessChecker, Lifecycle