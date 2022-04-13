package net.corda.v5.ledger.identity;

import net.corda.v5.base.types.MemberX500Name;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IdentityServiceJavaApiTest {
    private final IdentityService identityService = mock(IdentityService.class);
    private final MemberX500Name bobName = new MemberX500Name("Bob Plc", "Rome", "IT");
    private final Party bobParty = mock(Party.class);
    private final PublicKey bobPublicKey = mock(PublicKey.class);
    private final AnonymousParty anonymousParty = mock(AnonymousParty.class);
    private final UUID externalId = UUID.randomUUID();

    @Test
    public void nameFromKey() {
        when(identityService.nameFromKey(bobPublicKey)).thenReturn(bobName);

        MemberX500Name name = identityService.nameFromKey(bobPublicKey);

        Assertions.assertThat(name).isNotNull();
        Assertions.assertThat(name).isEqualTo(bobName);
    }

    @Test
    public void anonymousPartyFromKey() {
        when(identityService.anonymousPartyFromKey(bobPublicKey)).thenReturn(anonymousParty);

        AnonymousParty anonymousParty = identityService.anonymousPartyFromKey(bobPublicKey);

        Assertions.assertThat(anonymousParty).isNotNull();
        Assertions.assertThat(anonymousParty).isEqualTo(this.anonymousParty);
    }

    @Test
    public void partyForName() {
        when(identityService.partyFromName(bobName)).thenReturn(bobParty);

        Party party = identityService.partyFromName(bobName);

        Assertions.assertThat(party).isNotNull();
        Assertions.assertThat(party).isEqualTo(bobParty);
    }

    @Test
    public void partyFromAnonymous() {
        when(identityService.partyFromAnonymous(anonymousParty)).thenReturn(bobParty);

        Party party = identityService.partyFromAnonymous(anonymousParty);

        Assertions.assertThat(party).isNotNull();
        Assertions.assertThat(party).isEqualTo(bobParty);
    }

    @Test
    public void registerKeyExternalId() {
        identityService.registerKey(bobPublicKey, bobName, externalId);
        verify(identityService, times(1)).registerKey(bobPublicKey, bobName, externalId);
    }

    @Test
    public void registerKey() {
       identityService.registerKey(bobPublicKey, bobName);
       verify(identityService, times(1)).registerKey(bobPublicKey, bobName);
    }

    @Test
    public void externalIdForPublicKey() {
        when(identityService.externalIdForPublicKey(bobPublicKey)).thenReturn(externalId);

        UUID externalId = identityService.externalIdForPublicKey(bobPublicKey);

        Assertions.assertThat(externalId).isNotNull();
        Assertions.assertThat(externalId).isEqualTo(this.externalId);
    }

    @Test
    public void publicKeysForExternalId() {
        ArrayList<PublicKey> publicKeys = new ArrayList<>();
        publicKeys.add(bobPublicKey);
        when(identityService.publicKeysForExternalId(externalId)).thenReturn(publicKeys);

        ArrayList<PublicKey> keys = (ArrayList<PublicKey>) identityService.publicKeysForExternalId(externalId);

        Assertions.assertThat(keys).isNotNull();
        Assertions.assertThat(keys).isEqualTo(publicKeys);
    }
}
