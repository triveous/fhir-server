package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.enforcer.UnifiedEnforcer;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.PermissionGroup;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.util.stream.Collectors;

@Conditional(InboundSecurityAuthorizationCondition.class)
public class AuthorizationConfig {

	@Bean
	AuthorizationProperties authorizationProperties() {
		return new AuthorizationProperties();
	}

	@Bean
	AnonymousUserManagement anonymousUserManagement(AuthorizationProperties properties) {
		var permissions = properties.getAnonymousAccessPermission()
			.stream()
			.map(PermissionGroup::getName)
			.collect(Collectors.toSet());

		var permissionArgument = properties.getAnonymousAccessPermission()
			.stream()
			.map(p -> String.format("%s/%s", p.getName().name(), StringUtils.join(p.getArguments(), ",")))
			.collect(Collectors.toList());

		return new AnonymousUserManagement(
			properties.getAllowAnonymousAccess(),
			permissions,
			permissionArgument,
			properties.getAnonymousAccessPermissionRequired()
		);
	}

	@Bean
	UnifiedEnforcer unifiedEnforcer() {
		return new UnifiedEnforcer();
	}

	@Bean
	FHIREndpointAuthorizationInterceptor authorizationInterceptor(UnifiedEnforcer enforcer,
																					  AnonymousUserManagement anonymousUserManagement,
																					  AuthorizationProperties authorizationProperties) {
		return new FHIREndpointAuthorizationInterceptor(enforcer,
			anonymousUserManagement,
			authorizationProperties.getAnonymousAccessPermission()
		);
	}
}
