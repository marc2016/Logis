package app.logis.core.auth;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The stored passkeys as the WebAuthn library looks them up. */
@Component
class PasskeyCredentialRepository implements CredentialRepository {

    private final PasskeyStore store;

    PasskeyCredentialRepository(PasskeyStore store) {
        this.store = store;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return store.forUsername(username).stream()
                .map(p -> PublicKeyCredentialDescriptor.builder().id(new ByteArray(p.credentialId())).build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return store.userHandleForUsername(username).map(ByteArray::new);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return store.usernameForUserHandle(userHandle.getBytes());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return store.byCredentialId(credentialId.getBytes())
                .flatMap(p -> store.userHandle(p.userId())
                        .filter(h -> new ByteArray(h).equals(userHandle))
                        .map(h -> credential(p, h)));
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return store.byCredentialId(credentialId.getBytes())
                .flatMap(p -> store.userHandle(p.userId()).map(h -> credential(p, h)))
                .stream().collect(Collectors.toSet());
    }

    private static RegisteredCredential credential(PasskeyStore.StoredPasskey p, byte[] userHandle) {
        return RegisteredCredential.builder()
                .credentialId(new ByteArray(p.credentialId()))
                .userHandle(new ByteArray(userHandle))
                .publicKeyCose(new ByteArray(p.publicKeyCose()))
                .signatureCount(p.signCount())
                .build();
    }
}
