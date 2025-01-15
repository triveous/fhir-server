package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.enforcer;

import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.AuthenticationOutcome;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.UserSessionDetail;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilder;

public interface Enforcer {
    IAuthRuleBuilder enforce(AuthenticationOutcome outcome, UserSessionDetail.GrantedAuthority authority, IAuthRuleBuilder ruleBuilder);
}
