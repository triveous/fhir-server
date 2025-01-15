package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.UserSessionDetail;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.Permission;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class OIDCAuthenticationProtocol implements AuthenticationProtocol {
	private static final Logger log = LoggerFactory.getLogger(OIDCAuthenticationProtocol.class);
	private final JwtDecoder jwtDecoder;

	public OIDCAuthenticationProtocol(JwtDecoder jwtDecoder) {
		this.jwtDecoder = jwtDecoder;
	}

	private static final String name = "OAuth2";

	private static String getToken(RequestDetails requestDetails) {
		var authHeader = requestDetails.getHeader(HttpHeaders.AUTHORIZATION);
		if (isNotBlank(authHeader)) {
			var parts = authHeader.split(" ");
			if (parts.length < 2 || !parts[0].equalsIgnoreCase("bearer")) return null;
			return parts[1];
		}

		// Get the token from the query parameter
		var authHeaderTokens = requestDetails.getParameters().get("authToken");
		if (authHeaderTokens == null || authHeaderTokens.length == 0 || isBlank(authHeaderTokens[0])) return null;
		return authHeaderTokens[0];
	}

	@Override
	public boolean canAuthenticate(RequestDetails requestDetails) {
		return isNotBlank(getToken(requestDetails));
	}

	@Override
	public UserSessionDetail authenticate(RequestDetails requestDetails) {
		// Assuming this won't return a null or empty token as check might have been done using canAuthenticate
		var token = getToken(requestDetails);
		var jwt = jwtDecoder.decode(token);

		if (jwt.getSubject() == null) {
			throw new RuntimeException("Missing subject in jwt");
		}

		return UserSessionDetail.builder()
			.setId(jwt.getSubject())
			.setJwt(jwt)
			.setScopes(extractScopes(jwt))
			.setOidcClientId(jwt.getClaimAsString("azp"))
			.setAccountDisabled(false)
			.setEmail(jwt.getClaimAsString("email"))
			.setEmailVerified(jwt.getClaimAsBoolean("email_verified"))
			.setAuthorities(extractGrantedAuthorities(jwt))
			.setAccountLocked(false)
			.setGivenName(jwt.getClaimAsString("given_name"))
			.setFamilyName(jwt.getClaimAsString("family_name"))
			.setUsername(jwt.getClaimAsString("preferred_username"))
			.setUsernameNamespace(jwt.getClaimAsString("azp"))
			.setServiceAccount(false)
			.setFhirUserUrl(String.format("%s/%s", "Practitioner", jwt.getSubject()))
			.build();

	}

	private static Set<String> extractScopes(Jwt jwt) {
		try {
			return Arrays.stream(jwt.getClaimAsString("scope").split(" ")).collect(Collectors.toSet());
		} catch (Exception e) {
			log.error("Unable to extract scope", e);
			return Collections.emptySet();
		}
	}

	private static Set<UserSessionDetail.GrantedAuthority> extractGrantedAuthorities(Jwt jwt) {
		try {
			var realmAccess = jwt.getClaimAsMap("realm_access");
			//noinspection unchecked,MismatchedQueryAndUpdateOfCollection
			List<String> permissions = (realmAccess != null ?
				((List<String>) realmAccess.getOrDefault("roles", Collections.emptyList())) :
				Collections.emptyList());

			if (permissions.isEmpty()) return Collections.emptySet();

			var argumentMap = new HashSetValuedHashMap<String, String>();
			//noinspection unchecked
			var argument = ((List<String>) jwt.getClaim("permission_argument"));
			if (argument == null) argument = Collections.emptyList();

			argument.stream()
				.map(entry -> entry.split("/", 2))
				.filter(v -> v.length == 2)
				.forEach(v -> argumentMap.put(v[0], v[1]));

			// Only accept permission as granted authorities and not the role itself
			return permissions.stream()
				.map(p -> {
					try {
						return Permission.valueOf(p);
					} catch (IllegalArgumentException e) {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.map(p -> new UserSessionDetail.GrantedAuthority(p, argumentMap))
				.collect(Collectors.toSet());

		} catch (Exception e) {
			log.error("Unable to extract permission", e);
			return Collections.emptySet();
		}
	}

	@Override
	public String name() {
		return name;
	}
}