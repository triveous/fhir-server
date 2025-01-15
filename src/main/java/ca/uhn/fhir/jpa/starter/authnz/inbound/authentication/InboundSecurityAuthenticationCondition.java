package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class InboundSecurityAuthenticationCondition implements Condition {
	@Override
	public boolean matches(ConditionContext theConditionContext, AnnotatedTypeMetadata metadata) {
		String property = theConditionContext.getEnvironment().getProperty("hapi.fhir.security.inbound.authentication.enabled");
		return Boolean.parseBoolean(property);
	}
}
