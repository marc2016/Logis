package app.logis.core.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * A passkey on a test "device": answers WebAuthn options like a platform authenticator would ("none" attestation,
 * ES256, user presence and verification), so the server's real verification runs in tests.
 */
class SoftwareAuthenticator {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final String origin;
    private final KeyPair keys;
    private final byte[] credentialId = new byte[16];
    private byte[] userHandle;
    private long counter;

    SoftwareAuthenticator(String origin) throws Exception {
        this.origin = origin;
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        this.keys = generator.generateKeyPair();
        new SecureRandom().nextBytes(credentialId);
    }

    String credentialIdBase64() {
        return B64.encodeToString(credentialId);
    }

    /** The response to registration options (the `publicKey` part). */
    ObjectNode register(JsonNode options) throws Exception {
        userHandle = B64D.decode(options.get("user").get("id").asText());
        var rpId = options.get("rp").get("id").asText();
        var clientData = clientData("webauthn.create", options.get("challenge").asText());
        var authData = new ByteArrayOutputStream();
        authData.write(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
        authData.write(0x45); // user present, user verified, attested credential data
        authData.write(ByteBuffer.allocate(4).putInt((int) counter).array());
        authData.write(new byte[16]); // AAGUID
        authData.write(ByteBuffer.allocate(2).putShort((short) credentialId.length).array());
        authData.write(credentialId);
        authData.write(coseKey());
        var attestation = new Cbor().map(3)
                .text("fmt").text("none")
                .text("attStmt").map(0)
                .text("authData").bytes(authData.toByteArray())
                .toByteArray();
        var response = JSON.createObjectNode();
        response.put("clientDataJSON", B64.encodeToString(clientData));
        response.put("attestationObject", B64.encodeToString(attestation));
        response.putArray("transports").add("internal");
        // Fields browsers add through @simplewebauthn/browser, so the server is tested with them.
        response.put("authenticatorData", B64.encodeToString(authData.toByteArray()));
        response.put("publicKey", B64.encodeToString(keys.getPublic().getEncoded()));
        response.put("publicKeyAlgorithm", -7);
        return credential(response);
    }

    /** The response to sign-in options; {@code verified} says whether the user unlocked the device. */
    ObjectNode signIn(JsonNode options, boolean verified) throws Exception {
        var rpId = options.get("rpId").asText();
        counter++;
        var clientData = clientData("webauthn.get", options.get("challenge").asText());
        var authData = new ByteArrayOutputStream();
        authData.write(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
        authData.write(verified ? 0x05 : 0x01);
        authData.write(ByteBuffer.allocate(4).putInt((int) counter).array());
        var signed = new ByteArrayOutputStream();
        signed.write(authData.toByteArray());
        signed.write(sha256(clientData));
        var signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keys.getPrivate());
        signer.update(signed.toByteArray());
        var response = JSON.createObjectNode();
        response.put("clientDataJSON", B64.encodeToString(clientData));
        response.put("authenticatorData", B64.encodeToString(authData.toByteArray()));
        response.put("signature", B64.encodeToString(signer.sign()));
        response.put("userHandle", B64.encodeToString(userHandle));
        return credential(response);
    }

    /** A second device with the same credential id but another key: its signature cannot verify. */
    SoftwareAuthenticator forgery() throws Exception {
        var other = new SoftwareAuthenticator(origin);
        System.arraycopy(credentialId, 0, other.credentialId, 0, credentialId.length);
        other.userHandle = userHandle;
        return other;
    }

    /** A device with its own key that claims the credential id of another one (a passkey the server does not know). */
    SoftwareAuthenticator forgeryOf(SoftwareAuthenticator other) {
        new SecureRandom().nextBytes(credentialId);
        userHandle = other.userHandle;
        return this;
    }

    private ObjectNode credential(ObjectNode response) {
        var node = JSON.createObjectNode();
        node.put("id", B64.encodeToString(credentialId));
        node.put("rawId", B64.encodeToString(credentialId));
        node.put("type", "public-key");
        node.set("response", response);
        node.put("authenticatorAttachment", "platform");
        node.putObject("clientExtensionResults");
        return node;
    }

    private byte[] clientData(String type, String challenge) throws Exception {
        var node = JSON.createObjectNode();
        node.put("type", type);
        node.put("challenge", challenge);
        node.put("origin", origin);
        node.put("crossOrigin", false);
        return JSON.writeValueAsBytes(node);
    }

    private byte[] coseKey() {
        var point = ((ECPublicKey) keys.getPublic()).getW();
        return new Cbor().map(5)
                .integer(1).integer(2) // kty: EC2
                .integer(3).integer(-7) // alg: ES256
                .integer(-1).integer(1) // crv: P-256
                .integer(-2).bytes(fixed(point.getAffineX()))
                .integer(-3).bytes(fixed(point.getAffineY()))
                .toByteArray();
    }

    private static byte[] fixed(BigInteger value) {
        var raw = value.toByteArray();
        var out = new byte[32];
        var copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    /** Just enough CBOR for attestation objects and COSE keys. */
    private static final class Cbor {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private Cbor head(int major, long value) {
            if (value < 24) {
                out.write((major << 5) | (int) value);
            } else if (value < 256) {
                out.write((major << 5) | 24);
                out.write((int) value);
            } else {
                out.write((major << 5) | 25);
                out.write((int) (value >> 8));
                out.write((int) value);
            }
            return this;
        }

        Cbor integer(long value) {
            return value >= 0 ? head(0, value) : head(1, -1 - value);
        }

        Cbor bytes(byte[] value) {
            head(2, value.length);
            out.writeBytes(value);
            return this;
        }

        Cbor text(String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            head(3, bytes.length);
            out.writeBytes(bytes);
            return this;
        }

        Cbor map(int entries) {
            return head(5, entries);
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
