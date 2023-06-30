package net.corda.testing.driver;

import java.util.Set;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@SuppressWarnings("unused")
public final class EachTestDriver extends AbstractDriver implements BeforeEachCallback, AfterEachCallback {
    public EachTestDriver(@NotNull String schemeName, @NotNull Set<MemberX500Name> members) {
        super(schemeName, members);
    }

    public EachTestDriver(@NotNull String schemeName, @NotNull MemberX500Name member, MemberX500Name... members) {
        this(schemeName, setOf(member, members));
    }

    public EachTestDriver(@NotNull Set<MemberX500Name> members) {
        this(DEFAULT_SCHEME_NAME, members);
    }

    public EachTestDriver(@NotNull MemberX500Name member, MemberX500Name... members) {
        this(setOf(member, members));
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        createDriver(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        destroyDriver(context);
    }
}
