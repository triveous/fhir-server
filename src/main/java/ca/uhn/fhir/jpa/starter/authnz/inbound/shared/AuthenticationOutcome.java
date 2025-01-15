package ca.uhn.fhir.jpa.starter.authnz.inbound.shared;

import ca.uhn.fhir.rest.api.server.RequestDetails;

import java.util.Objects;

public class AuthenticationOutcome {
	private static final String AUTHENTICATION_OUTCOME_DETAIL_KEY = AuthenticationOutcome.class.getSimpleName();

	private final UserSessionDetail userSessionDetail;
	private final boolean isAuthenticated;
	private final boolean authenticationFailed;

	public AuthenticationOutcome(UserSessionDetail userSessionDetail, boolean isAuthenticated, boolean authenticationFailed) {
		this.userSessionDetail = userSessionDetail;
		this.isAuthenticated = isAuthenticated;
		this.authenticationFailed = authenticationFailed;
	}

	public boolean hasPermission(Permission permission) {
		if (userSessionDetail == null) return false;

		return userSessionDetail.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getPermission(), permission));
	}

	public static AuthenticationOutcome getOutcome(RequestDetails requestDetails) {
		return (AuthenticationOutcome) requestDetails.getUserData().get(AUTHENTICATION_OUTCOME_DETAIL_KEY);
	}

	public static void setOutcome(RequestDetails requestDetails, AuthenticationOutcome outcome) {
		requestDetails.getUserData().put(AUTHENTICATION_OUTCOME_DETAIL_KEY, outcome);
	}

	public UserSessionDetail getUserSessionDetail() {
		return userSessionDetail;
	}

	public boolean isAuthenticated() {
		return isAuthenticated;
	}

	public boolean isAuthenticationFailed() {
		return authenticationFailed;
	}
}