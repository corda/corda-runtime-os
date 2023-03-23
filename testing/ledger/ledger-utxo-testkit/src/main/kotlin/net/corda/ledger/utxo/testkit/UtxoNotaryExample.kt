package net.corda.ledger.utxo.testkit

import net.corda.v5.base.types.MemberX500Name

val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
val anotherNotaryX500Name = MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB")