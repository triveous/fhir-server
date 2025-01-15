package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hapi.fhir.security.inbound.authentication")
public class AuthenticationProperties {
	// Convert the user to be anonymous when the authentication fails with auth error
	private Boolean proceedToAuthorizationOnFailure = false;
	private Boolean proceedToAuthorizationOnNoAuth = false;
	private Boolean enabled = false;
	private String userRealmUri;

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
}
