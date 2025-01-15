package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.AuthenticationOutcome;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.UserSessionDetail;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ca.uhn.fhir.jpa.starter.authnz.inbound.shared.AuthenticationOutcome.setOutcome;

public class AuthenticationService {
	private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
	private final AuthenticationProtocol.Registry registry;
	private final Boolean shouldSwitchToAnonymousUserOnAuthError;

	public AuthenticationService(AuthenticationProtocol.Registry registry, Boolean shouldSwitchToAnonymousUserOnAuthError) {
		this.registry = registry;
		this.shouldSwitchToAnonymousUserOnAuthError = shouldSwitchToAnonymousUserOnAuthError;
	}

	public Boolean getShouldSwitchToAnonymousUserOnAuthError() {
		return shouldSwitchToAnonymousUserOnAuthError;
	}

	public void perform(RequestDetails requestDetails) {
		var authFailed = false;
		for (AuthenticationProtocol protocol : registry.getAll()) {
			UserSessionDetail userSessionDetail = null;
			try {
				if (protocol.canAuthenticate(requestDetails)) {
					userSessionDetail = protocol.authenticate(requestDetails);
				}
			} catch (Exception e) {
				authFailed = true;
				log.info("Failed to authenticate", e);
			}

			if (userSessionDetail != null) {
				onAuthenticated(requestDetails, userSessionDetail);
				return;
			}
		}
		onNotAuthenticated(requestDetails, authFailed);
	}

	private void onAuthenticated(RequestDetails requestDetails, UserSessionDetail userSessionDetail) {
		var outcome = new AuthenticationOutcome(userSessionDetail, true, false);
		setOutcome(requestDetails, outcome);
	}

	private void onNotAuthenticated(RequestDetails requestDetails, boolean authFailed) {
		var outcome = new AuthenticationOutcome(null, false, authFailed);
		setOutcome(requestDetails, outcome);
	}
}
