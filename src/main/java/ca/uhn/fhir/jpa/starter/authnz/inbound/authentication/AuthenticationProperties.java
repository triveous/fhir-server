package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "hapi.fhir.security.inbound.authentication")
public class AuthenticationProperties {
	// Convert the user to be anonymous when the authentication fails with auth error
	private Boolean proceedToAuthorizationOnFailure = false;
	private Boolean proceedToAuthorizationOnNoAuth = false;
	private Boolean enabled = false;
	private String userRealmUri;
	// Multi-tenant: regex patterns matching allowed Keycloak realm issuer URLs.
	// When non-empty, userRealmUri is ignored and each JWT's 'iss' is validated against these patterns.
	private List<String> allowedIssuerPatterns = new ArrayList<>();

	public Boolean getProceedToAuthorizationOnFailure() {
		return proceedToAuthorizationOnFailure;
	}

	public void setProceedToAuthorizationOnFailure(Boolean proceedToAuthorizationOnFailure) {
		this.proceedToAuthorizationOnFailure = proceedToAuthorizationOnFailure;
	}

	public Boolean getProceedToAuthorizationOnNoAuth() {
		return proceedToAuthorizationOnNoAuth;
	}

	public void setProceedToAuthorizationOnNoAuth(Boolean proceedToAuthorizationOnNoAuth) {
		this.proceedToAuthorizationOnNoAuth = proceedToAuthorizationOnNoAuth;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public String getUserRealmUri() {
		return userRealmUri;
	}

	public void setUserRealmUri(String userRealmUri) {
		this.userRealmUri = userRealmUri;
	}

	public List<String> getAllowedIssuerPatterns() {
		return allowedIssuerPatterns;
	}

	public void setAllowedIssuerPatterns(List<String> allowedIssuerPatterns) {
		this.allowedIssuerPatterns = allowedIssuerPatterns;
	}
}
