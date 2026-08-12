package com.pixous.hrportal.modules.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Keeps a readable-back copy of each account's current password, so HR and the
 * admin can see the actual password on an employee's record rather than only
 * being able to replace it.
 *
 * <p>Signing in is unaffected: it still checks the BCrypt hash in
 * {@code users.password_hash}, which stays exactly as it was. This is a second,
 * reversible copy kept beside it, written every time a password is set — on
 * joining, on an admin reset, and when an employee changes their own.
 *
 * <p>It is encrypted rather than stored as text. AES-GCM under a key derived
 * from {@code APP_JWT_SECRET}, so a database dump on its own — including the
 * pre-deploy backup that lands in {@code ~/backups} — carries nothing readable;
 * the application's secret is needed as well. Anyone holding both can read
 * every password, which is the cost of showing them at all.
 */
@Component
public class PasswordVault {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PasswordVault(@Value("${app.jwt.secret}") String appSecret) {
        // A distinct label mixed in, so this key is not the token-signing key
        // itself even though it is grown from the same secret.
        this.key = new SecretKeySpec(sha256("pixous:password-vault:v1:" + appSecret), "AES");
    }

    /** Encrypts a password for storage. Returns {@code null} for nothing to store. */
    public String seal(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // Never let this stop a password from being set: the hash is what
            // logging in depends on, and this copy is a convenience.
            return null;
        }
    }

    /**
     * Reads a stored password back. Returns {@code null} when there is nothing
     * stored, or when the stored value cannot be opened with the current secret
     * — which is what happens to rows written before this existed, and to every
     * row if APP_JWT_SECRET is ever changed.
     */
    public String open(String sealed) {
        if (sealed == null || sealed.isBlank()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(sealed);
            if (all.length <= IV_BYTES) return null;
            byte[] iv = new byte[IV_BYTES];
            byte[] cipherText = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
