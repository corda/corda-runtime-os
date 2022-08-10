@Export
package net.corda.ledger.common.impl.transaction;

import org.osgi.annotation.bundle.Export;

/*
For
    [<<INITIAL>>]
      ⇒ osgi.identity: (osgi.identity=net.corda.ledger-consensual-impl)
          ⇒ [net.corda.ledger-consensual-impl version=5.0.0.0-SNAPSHOT]
              ⇒ osgi.wiring.package: (osgi.wiring.package=net.corda.ledger.common.impl.transactions)

 */