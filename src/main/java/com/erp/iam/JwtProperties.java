package com.erp.iam;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings, bound from {@code app.jwt.*} (see application.properties).
 *
 * @param secret       HMAC (HS256) signing secret — at least 32 bytes. Supplied via {@code APP_JWT_SECRET}
 *                     in deployed environments; the in-file default is for local dev only.
 * @param accessTtl    access-token lifetime (short, e.g. PT15M)
 * @param refreshTtl   refresh-token lifetime (long, e.g. P7D)
 * @param cookieSecure mark the refresh cookie {@code Secure}. False for the plain-HTTP local demo; true in
 *                     production (the browser is HTTPS even though the app sees HTTP behind the proxy).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl, boolean cookieSecure) {}
