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

		// No URL tenant segment → admin / DEFAULT-partition call; nothing to enforce here.
		if (urlTenantId == null) return;

		// Explicit DEFAULT in the URL is treated the same as no tenant segment: shared-partition
		// admin access. Tokens without a partition_id are permitted to reach DEFAULT this way.
		if ("DEFAULT".equalsIgnoreCase(urlTenantId)) return;

		// Any other tenant URL requires the JWT to carry partition_id. Failing open here would
		// let a token with no claim address every tenant — defeating partition isolation.
		if (jwtPartitionId == null) {
			throw new ForbiddenOperationException(
				"Authenticated request to tenant '" + urlTenantId + "' rejected: JWT is missing partition_id claim");
		}

		if (!jwtPartitionId.equalsIgnoreCase(urlTenantId)) {
			throw new ForbiddenOperationException(
				"JWT partition_id '" + jwtPartitionId + "' does not match requested tenant '" + urlTenantId + "'");
		}
	}
}
