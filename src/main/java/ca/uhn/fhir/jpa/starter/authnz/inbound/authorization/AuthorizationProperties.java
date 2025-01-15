package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.PermissionGroup;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Set;

@ConfigurationProperties(prefix = "hapi.fhir.security.inbound.authorization")
public class AuthorizationProperties {

	private Set<PermissionGroup> anonymousAccessPermission = Collections.emptySet();
	private Boolean allowAnonymousAccess = true;
	private Boolean anonymousAccessPermissionRequired = false;

	private Boolean enabled;

	public Set<PermissionGroup> getAnonymousAccessPermission() {
		return anonymousAccessPermission;
	}

	public void setAnonymousAccessPermission(Set<PermissionGroup> anonymousAccessPermission) {
		this.anonymousAccessPermission = anonymousAccessPermission;
	}

	public Boolean getAllowAnonymousAccess() {
		return allowAnonymousAccess;
	}

	public void setAllowAnonymousAccess(Boolean allowAnonymousAccess) {
		this.allowAnonymousAccess = allowAnonymousAccess;
	}

	public Boolean getAnonymousAccessPermissionRequired() {
		return anonymousAccessPermissionRequired;
	}

	public void setAnonymousAccessPermissionRequired(Boolean anonymousAccessPermissionRequired) {
		this.anonymousAccessPermissionRequired = anonymousAccessPermissionRequired;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}
}
