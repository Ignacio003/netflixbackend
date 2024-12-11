package org.example.api;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;

public class JwtUtils {
    private static final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    private static final long TOKEN_EXPIRATION_MILLIS = 30 * 60 * 1000;

    public static Key getSecretKey() {
        return SECRET_KEY;
    }
    public static long getTokenExpiration(){
        return TOKEN_EXPIRATION_MILLIS;
    }
}