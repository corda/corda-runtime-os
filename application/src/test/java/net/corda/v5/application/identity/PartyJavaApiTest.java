package net.corda.v5.application.identity;

import net.corda.v5.base.types.MemberX500Name;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PartyJavaApiTest {
    private final Party party = mock(Party.class);

    @Test
    public void getName() {
        MemberX500Name testMemberX500Name = new MemberX500Name("Bob Plc", "Rome", "IT");
        when(party.getName()).thenReturn(testMemberX500Name);

        MemberX500Name result = party.getName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testMemberX500Name);
    }

    @Test
    public void anonymise() {
        AnonymousParty testAnonymousParty = mock(AnonymousParty.class);
        when(party.anonymise()).thenReturn(testAnonymousParty);

        AnonymousParty result = party.anonymise();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testAnonymousParty);
    }

    @Test
    public void description() {
        String test = "test";
        when(party.description()).thenReturn(test);

        String result = party.description();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }
}
