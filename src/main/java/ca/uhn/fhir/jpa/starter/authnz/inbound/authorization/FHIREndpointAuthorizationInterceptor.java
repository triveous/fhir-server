package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.enforcer.UnifiedEnforcer;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.AuthenticationOutcome;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.Permission;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.PermissionGroup;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.UserSessionDetail;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ca.uhn.fhir.jpa.starter.authnz.inbound.shared.AuthenticationOutcome.getOutcome;


/**
 * Allow or deny access based on the user granted authority or client scope
 */
@Interceptor(order = AuthorizationConstants.ORDER_AUTH_INTERCEPTOR)
public class FHIREndpointAuthorizationInterceptor extends AuthorizationInterceptor {
    private final UnifiedEnforcer unifiedEnforcer;
    private final AnonymousUserManagement anonymousUserManagement;
    private final Set<PermissionGroup> accessPermissions;

	public FHIREndpointAuthorizationInterceptor(UnifiedEnforcer unifiedEnforcer, AnonymousUserManagement anonymousUserManagement, Set<PermissionGroup> accessPermissions) {
		this.unifiedEnforcer = unifiedEnforcer;
		this.anonymousUserManagement = anonymousUserManagement;
		this.accessPermissions = accessPermissions;
	}

	@Override
    public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
        var outcome = getOutcome(theRequestDetails);
        var userDetail = outcome.getUserSessionDetail();
        IAuthRuleBuilder ruleBuilder = new RuleBuilder();
        if (!outcome.isAuthenticated() || userDetail == null) {
            return buildAnonymousAccessRules(ruleBuilder, outcome);
        }

        return buildAuthenticatedAccessRules(ruleBuilder, userDetail, outcome);
    }

    private List<IAuthRule> buildAuthenticatedAccessRules(IAuthRuleBuilder ruleBuilder, UserSessionDetail userDetail, AuthenticationOutcome outcome) {
        userDetail.getAuthorities().addAll(anonymousUserManagement.getAuthorities());
        ruleBuilder = attachAccessPermissionRules(userDetail.getAuthorities(), ruleBuilder);
        return prepareBuilder(userDetail.getAuthorities(), ruleBuilder, outcome).build();
    }

    private List<IAuthRule> buildAnonymousAccessRules(IAuthRuleBuilder ruleBuilder, AuthenticationOutcome outcome) {
        // User is not authenticated. We will allow access only if anonymous access is allowed
        if (!anonymousUserManagement.isAnonymousAccessAllowed()) {
            return ruleBuilder.denyAll("NO_ANONYMOUS_ACCESS").build();
        }
        if (anonymousUserManagement.isAccessPermissionNeeded()) {
            ruleBuilder = attachAccessPermissionRules(anonymousUserManagement.getAuthorities(), ruleBuilder);
        }
        return prepareBuilder(anonymousUserManagement.getAuthorities(), ruleBuilder, outcome).build();
    }

    private IAuthRuleBuilder prepareBuilder(Set<UserSessionDetail.GrantedAuthority> authorities, IAuthRuleBuilder ruleBuilder, AuthenticationOutcome outcome) {
        var negativePermission = authorities.stream()
                .filter(UserSessionDetail.GrantedAuthority::isNegativePermission)
                .collect(Collectors.toList());

        ruleBuilder = unifiedEnforcer.enforce(outcome, negativePermission, ruleBuilder);

        var positivePermission = authorities.stream()
                .filter(g -> !g.isNegativePermission())
                .collect(Collectors.toList());
        ruleBuilder = unifiedEnforcer.enforce(outcome, positivePermission, ruleBuilder);
        return ruleBuilder;
    }

    private IAuthRuleBuilder attachAccessPermissionRules(Set<UserSessionDetail.GrantedAuthority> authorities, IAuthRuleBuilder ruleBuilder) {
        var permissions  = accessPermissions.stream().map(PermissionGroup::getName).collect(Collectors.toList());
        for (Permission p : permissions) {
            var hasPermission = authorities.stream().anyMatch(auth -> auth.getPermission().equals(p));
            if (!hasPermission) {
                return ruleBuilder.denyAll("NO_ACCESS_PERMISSION").andThen();
            }
        }
        return ruleBuilder;
    }
}