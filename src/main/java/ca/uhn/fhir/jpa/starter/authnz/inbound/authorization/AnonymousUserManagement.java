package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.Permission;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.UserSessionDetail;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AnonymousUserManagement {
	private final boolean isAnonymousAccessAllowed;
	private final Set<UserSessionDetail.GrantedAuthority> authorities;
	private final boolean isAccessPermissionNeeded;

	public AnonymousUserManagement(Boolean isAnonymousAccessAllowed,
											 Set<Permission> permissions,
											 List<String> permissionArgument,
											 Boolean isAccessPermissionNeeded) {
		this.isAnonymousAccessAllowed = isAnonymousAccessAllowed;
		authorities = anonymousUserGrantedAuthorizes(permissions, permissionArgument);
		this.isAccessPermissionNeeded = isAccessPermissionNeeded;
	}

	public boolean isAnonymousAccessAllowed() {
		return isAnonymousAccessAllowed;
	}

	public Set<UserSessionDetail.GrantedAuthority> getAuthorities() {
		return authorities;
	}

	public boolean isAccessPermissionNeeded() {
		return isAccessPermissionNeeded;
	}

	private static Set<UserSessionDetail.GrantedAuthority> anonymousUserGrantedAuthorizes(
		Set<Permission> permissions,
		List<String> argument) {

		var permissionArgument = new HashSetValuedHashMap<String, String>();
		argument.stream()
			.map(entry -> entry.split("/", 2))
			.filter(v -> v.length == 2)
			.forEach(v -> permissionArgument.put(v[0], v[1]));

		return permissions
			.stream()
			.filter(Objects::nonNull)
			.map(p -> new UserSessionDetail.GrantedAuthority(p, permissionArgument))
			.collect(Collectors.toSet());
	}
}
