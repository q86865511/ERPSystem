package com.erp.iam.application;

import com.erp.iam.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Mints and validates HS256 JWTs and builds the refresh cookie. The access token carries {@code sub}
 * (username) and a {@code roles} claim (role names without the {@code ROLE_} prefix; empty for guest),
 * which {@code SecurityConfig}'s converter maps to {@code ROLE_*} authorities. The refresh token carries
 * {@code typ=refresh} and only the subject; it lives in an httpOnly, SameSite=Lax cookie scoped to
 * {@code /api/auth}.
 */
@Service
public class JwtService {

    public static final String REFRESH_COOKIE = "refresh_token";
    private static final String ROLES_CLAIM = "roles";
    private static final String TYPE_CLAIM = "typ";
    private static final String REFRESH_TYPE = "refresh";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties props;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder, JwtProperties props) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.props = props;
    }

    public String createAccessToken(String username, Collection<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .claim(ROLES_CLAIM, List.copyOf(roles))
                .build();
        return encode(claims);
    }

    public String createRefreshToken(String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(props.refreshTtl()))
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .build();
        return encode(claims);
    }

    /** Validates a refresh token (signature + expiry + {@code typ=refresh}) and returns its subject. */
    public String parseRefreshSubject(String token) {
        Jwt jwt = decoder.decode(token); // throws JwtException on bad signature / expiry
        if (!REFRESH_TYPE.equals(jwt.getClaimAsString(TYPE_CLAIM))) {
            throw new BadJwtException("Not a refresh token");
        }
        return jwt.getSubject();
    }

    public ResponseCookie buildRefreshCookie(String token) {
        return baseCookie(token).maxAge(props.refreshTtl()).build();
    }

    public ResponseCookie clearRefreshCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .path("/api/auth")
                .sameSite("Lax");
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
