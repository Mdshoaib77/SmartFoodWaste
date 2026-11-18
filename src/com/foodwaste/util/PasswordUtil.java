package com.foodwaste.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted PBKDF2-HMAC-SHA256 password hashing. Stored form: iterations:saltBase64:hashBase64 */
public final class PasswordUtil {
    private static final int ITERATIONS = 65536;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordUtil() { }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        byte[] h = derive(password.toCharArray(), salt, ITERATIONS);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(h);
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] p = stored.split(":");
        if (p.length != 3) return false;
        try {
            int it = Integer.parseInt(p[0]);
            byte[] salt = Base64.getDecoder().decode(p[1]);
            byte[] expected = Base64.getDecoder().decode(p[2]);
            return MessageDigest.isEqual(expected, derive(password.toCharArray(), salt, it));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(char[] pw, byte[] salt, int iterations) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(new PBEKeySpec(pw, salt, iterations, KEY_BITS)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }
}
