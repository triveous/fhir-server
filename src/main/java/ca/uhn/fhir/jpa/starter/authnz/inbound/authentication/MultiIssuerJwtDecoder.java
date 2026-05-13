package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import com.nimbusds.jwt.JWTParser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;

import java.text.ParseException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MultiIssuerJwtDecoder implements JwtDecoder {
	private final List<Pattern> allowedIssuerPatterns;
	private final ConcurrentHashMap<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();

	public MultiIssuerJwtDecoder(List<String> allowedIssuerPatterns) {
		this.allowedIssuerPatterns = allowedIssuerPatterns.stream()
			.map(Pattern::compile)
			.collect(Collectors.toList());
	}

	@Override
	public Jwt decode(String token) throws JwtException {
		String issuer = extractIssuer(token);
		validateIssuer(issuer);
		JwtDecoder decoder = decoderCache.computeIfAbsent(issuer, JwtDecoders::fromIssuerLocation);
		return decoder.decode(token);
	}

	private String extractIssuer(String token) {
		try {
			String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
			if (issuer == null || issuer.isBlank()) {
				throw new JwtException("JWT is missing required 'iss' claim");
			}
			return issuer;
		} catch (ParseException e) {
			throw new JwtException("Failed to parse JWT: " + e.getMessage(), e);
		}
	}

	private void validateIssuer(String issuer) {
		boolean allowed = allowedIssuerPatterns.stream().anyMatch(p -> p.matcher(issuer).matches());
		if (!allowed) {
			throw new JwtException("JWT issuer '" + issuer + "' does not match any configured allowed-issuer-patterns");
		}
	}
}
