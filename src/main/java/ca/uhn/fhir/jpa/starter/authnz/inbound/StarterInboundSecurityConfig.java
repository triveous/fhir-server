package ca.uhn.fhir.jpa.starter.authnz.inbound;

import ca.uhn.fhir.jpa.starter.authnz.inbound.authentication.AuthenticationConfig;
import ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.AuthorizationConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({AuthenticationConfig.class, AuthorizationConfig.class})
public class StarterInboundSecurityConfig {
}
