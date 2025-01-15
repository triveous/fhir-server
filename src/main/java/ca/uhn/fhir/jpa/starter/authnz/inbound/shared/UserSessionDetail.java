package ca.uhn.fhir.jpa.starter.authnz.inbound.shared;

import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.collections4.MultiValuedMap;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public class UserSessionDetail {
	private final String id;
	private final Jwt jwt;
	private final List<FhirContext> fhirContext;
	private final Set<String> scopes;
	private final String oidcClientId;
	private final Map<String, Object> userData = new HashMap<>();
	private final Boolean accountDisabled;
	private final String email;
	private final Boolean emailVerified;
	private final Boolean accountLocked;
	private final Set<GrantedAuthority> authorities;
	private final String givenName;
	private final String familyName;
	private final String username;
	private final String usernameNamespace;
	private final String systemUser;
	private final Boolean serviceAccount;
	private final String fhirUserUrl;

	private UserSessionDetail(String id, Jwt jwt, List<FhirContext> fhirContext, Set<String> scopes, String oidcClientId, Boolean accountDisabled, String email, Boolean emailVerified, Boolean accountLocked, Set<GrantedAuthority> authorities, String givenName, String familyName, String username, String usernameNamespace, String systemUser, Boolean serviceAccount, String fhirUserUrl) {
		this.id = id;
		this.jwt = jwt;
		this.fhirContext = fhirContext;
		this.scopes = scopes;
		this.oidcClientId = oidcClientId;
		this.accountDisabled = accountDisabled;
		this.email = email;
		this.emailVerified = emailVerified;
		this.accountLocked = accountLocked;
		this.authorities = authorities;
		this.givenName = givenName;
		this.familyName = familyName;
		this.username = username;
		this.usernameNamespace = usernameNamespace;
		this.systemUser = systemUser;
		this.serviceAccount = serviceAccount;
		this.fhirUserUrl = fhirUserUrl;
	}

	public String getId() {
		return id;
	}

	public Jwt getJwt() {
		return jwt;
	}

	public List<FhirContext> getFhirContext() {
		return fhirContext;
	}

	public Set<String> getScopes() {
		return scopes;
	}

	public String getOidcClientId() {
		return oidcClientId;
	}

	public Map<String, Object> getUserData() {
		return userData;
	}

	public Boolean getAccountDisabled() {
		return accountDisabled;
	}

	public String getEmail() {
		return email;
	}

	public Boolean getEmailVerified() {
		return emailVerified;
	}

	public Boolean getAccountLocked() {
		return accountLocked;
	}

	public Set<GrantedAuthority> getAuthorities() {
		return authorities;
	}

	public String getGivenName() {
		return givenName;
	}

	public String getFamilyName() {
		return familyName;
	}

	public String getUsername() {
		return username;
	}

	public String getUsernameNamespace() {
		return usernameNamespace;
	}

	public String getSystemUser() {
		return systemUser;
	}

	public Boolean getServiceAccount() {
		return serviceAccount;
	}

	public String getFhirUserUrl() {
		return fhirUserUrl;
	}


	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String id;
		private Jwt jwt;
		private List<FhirContext> fhirContext;
		private Set<String> scopes;
		private String oidcClientId;
		private Boolean accountDisabled;
		private String email;
		private Boolean emailVerified;
		private Boolean accountLocked;
		private Set<UserSessionDetail.GrantedAuthority> authorities;
		private String givenName;
		private String familyName;
		private String username;
		private String usernameNamespace;
		private String systemUser;
		private Boolean serviceAccount;
		private String fhirUserUrl;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setJwt(Jwt jwt) {
			this.jwt = jwt;
			return this;
		}

		public Builder setFhirContext(List<FhirContext> fhirContext) {
			this.fhirContext = fhirContext;
			return this;
		}

		public Builder setScopes(Set<String> scopes) {
			this.scopes = scopes;
			return this;
		}

		public Builder setOidcClientId(String oidcClientId) {
			this.oidcClientId = oidcClientId;
			return this;
		}

		public Builder setAccountDisabled(Boolean accountDisabled) {
			this.accountDisabled = accountDisabled;
			return this;
		}

		public Builder setEmail(String email) {
			this.email = email;
			return this;
		}

		public Builder setEmailVerified(Boolean emailVerified) {
			this.emailVerified = emailVerified;
			return this;
		}

		public Builder setAccountLocked(Boolean accountLocked) {
			this.accountLocked = accountLocked;
			return this;
		}

		public Builder setAuthorities(Set<UserSessionDetail.GrantedAuthority> authorities) {
			this.authorities = authorities;
			return this;
		}

		public Builder setGivenName(String givenName) {
			this.givenName = givenName;
			return this;
		}

		public Builder setFamilyName(String familyName) {
			this.familyName = familyName;
			return this;
		}

		public Builder setUsername(String username) {
			this.username = username;
			return this;
		}

		public Builder setUsernameNamespace(String usernameNamespace) {
			this.usernameNamespace = usernameNamespace;
			return this;
		}

		public Builder setSystemUser(String systemUser) {
			this.systemUser = systemUser;
			return this;
		}

		public Builder setServiceAccount(Boolean serviceAccount) {
			this.serviceAccount = serviceAccount;
			return this;
		}

		public Builder setFhirUserUrl(String fhirUserUrl) {
			this.fhirUserUrl = fhirUserUrl;
			return this;
		}

		public UserSessionDetail build() {
			return new UserSessionDetail(id, jwt, fhirContext, scopes, oidcClientId, accountDisabled, email, emailVerified, accountLocked, authorities, givenName, familyName, username, usernameNamespace, systemUser, serviceAccount, fhirUserUrl);
		}
	}


	public static class GrantedAuthority {
		private final Permission permission;
		private final MultiValuedMap<String, String> argumentMap;

		public GrantedAuthority(Permission permission, MultiValuedMap<String, String> argumentMap) {
			this.permission = permission;
			this.argumentMap = argumentMap;
		}

		public Collection<String> getArgument() {
			return argumentMap.containsKey(permission.name()) ? argumentMap.get(permission.name()) : Collections.emptyList();
		}

		public boolean isNegativePermission() {
			return permission.name().toUpperCase().contains("BLOCK");
		}

		public Permission getPermission() {
			return permission;
		}

		public MultiValuedMap<String, String> getArgumentMap() {
			return argumentMap;
		}
	}
}
