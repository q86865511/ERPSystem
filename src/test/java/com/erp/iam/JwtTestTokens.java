package com.erp.iam;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Instant;
import java.util.List;

/**
 * Mints real access tokens in tests using the app's {@link JwtEncoder} bean, so assertions exercise the
 * genuine resource-server filter + roles→authorities converter (rather than @WithMockUser shortcuts).
 */
final class JwtTestTokens {

    private JwtTestTokens() {}

    static String access(JwtEncoder encoder, String username, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(roles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    static String bearer(JwtEncoder encoder, String username, String... roles) {
        return "Bearer " + access(encoder, username, roles);
    }
}
