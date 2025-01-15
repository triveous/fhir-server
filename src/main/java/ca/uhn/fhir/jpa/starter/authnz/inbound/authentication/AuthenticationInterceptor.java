package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.AuthenticationOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationConstants;

@Interceptor(order = AuthorizationConstants.ORDER_AUTH_INTERCEPTOR - 1)
public class AuthenticationInterceptor {
	private final AuthenticationService authenticationService;
	private final Boolean allowOnAuthFailure;
	private final Boolean allowOnNoAuth;

	public AuthenticationInterceptor(AuthenticationService authenticationService, Boolean allowOnAuthFailure, Boolean allowOnNoAuth) {
		this.authenticationService = authenticationService;
		this.allowOnAuthFailure = allowOnAuthFailure;
		this.allowOnNoAuth = allowOnNoAuth;
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void incomingRequestPreHandled(RequestDetails theRequest) {
		authenticationService.perform(theRequest);
		var outcome = AuthenticationOutcome.getOutcome(theRequest);
		if (outcome == null) {
			throw new RuntimeException("Invalid error");
		}
		if (outcome.isAuthenticated()) return;

		// User didn't provide credential for this request
		if (!outcome.isAuthenticationFailed() && allowOnNoAuth) return;

		// User provided credential but system was unable to verify it. Allow  to
		// proceed so that authorization system can see if this is an anonymous user
		// request like metadata
		if (outcome.isAuthenticationFailed() && allowOnAuthFailure) return;

		throw new AuthenticationException("Invalid credentials");
	}
}
