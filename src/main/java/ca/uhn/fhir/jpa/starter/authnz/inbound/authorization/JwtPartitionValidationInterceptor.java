package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.AuthenticationOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationConstants;

@Interceptor(order = AuthorizationConstants.ORDER_AUTH_INTERCEPTOR)
public class JwtPartitionValidationInterceptor {

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void validate(RequestDetails requestDetails) {
		AuthenticationOutcome outcome = AuthenticationOutcome.getOutcome(requestDetails);
		if (outcome == null || !outcome.isAuthenticated()) return;

		String jwtPartitionId = outcome.getUserSessionDetail().getPartitionId();
		String urlTenantId = requestDetails.getTenantId();

		// Skip when no partition claim or no URL tenant — covers DEFAULT partition admin calls
		if (jwtPartitionId == null || urlTenantId == null) return;

		if (!jwtPartitionId.equalsIgnoreCase(urlTenantId)) {
			throw new ForbiddenOperationException(
				"JWT partition_id '" + jwtPartitionId + "' does not match requested tenant '" + urlTenantId + "'");
		}
	}
}
