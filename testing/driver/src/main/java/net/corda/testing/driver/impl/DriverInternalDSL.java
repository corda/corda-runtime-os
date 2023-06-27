package net.corda.testing.driver.impl;

import net.corda.testing.driver.DriverDSL;
import net.corda.testing.driver.Framework;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

/**
 * This internal interface is package private,
 * to keep it hidden from Java code.
 */
interface DriverInternalDSL extends DriverDSL {
    @NotNull
    Framework getFramework(@NotNull MemberX500Name x500Name);
}
