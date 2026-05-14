package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.interceptor.partition.RequestTenantPartitionInterceptor;

// Stock RequestTenantPartitionInterceptor throws when a request has no tenant.
// Server-internal work (e.g. search-parameter reindex on startup) issues
// SystemRequestDetails without a URL tenant — route those to all partitions
// instead so partitioning can be enabled without breaking boot.
public class SystemAwareRequestTenantPartitionInterceptor extends RequestTenantPartitionInterceptor {

	@Override
	protected RequestPartitionId extractPartitionIdFromRequest(RequestDetails theRequestDetails) {
		if (theRequestDetails instanceof SystemRequestDetails) {
			return RequestPartitionId.allPartitions();
		}
		return super.extractPartitionIdFromRequest(theRequestDetails);
	}
}
