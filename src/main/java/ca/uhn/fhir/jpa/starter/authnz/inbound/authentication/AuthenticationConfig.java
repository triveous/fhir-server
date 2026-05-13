package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.JwtPartitionValidationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

@Conditional(InboundSecurityAuthenticationCondition.class)
public class AuthenticationConfig {

    @Bean
    AuthenticationProperties authenticationProperties() {
        return new AuthenticationProperties();
    }

    @Bean
    AuthenticationService authenticationService(AuthenticationProtocol.Registry registry,
                                                AuthenticationProperties properties) {
        return new AuthenticationService(registry, properties.getProceedToAuthorizationOnFailure());
    }

    @Bean
    AuthenticationInterceptor authenticationInterceptor(AuthenticationService authenticationService,
                                                        AuthenticationProperties properties) {
        return new AuthenticationInterceptor(
                authenticationService,
                properties.getProceedToAuthorizationOnFailure(),
                properties.getProceedToAuthorizationOnNoAuth());
    }

    @Bean
    public JwtDecoder jwtDecoder(AuthenticationProperties properties) {
        if (!properties.getAllowedIssuerPatterns().isEmpty()) {
            return new MultiIssuerJwtDecoder(properties.getAllowedIssuerPatterns());
        }
        return JwtDecoders.fromIssuerLocation(properties.getUserRealmUri());
    }

    @Bean
    public OIDCAuthenticationProtocol oidcAuthenticationProtocol(JwtDecoder jwtDecoder) {
        return new OIDCAuthenticationProtocol(jwtDecoder);
    }

    @Bean
    public AuthenticationProtocol.Registry registry(OIDCAuthenticationProtocol authenticationProtocol) {
        return new AuthenticationProtocol.Registry(authenticationProtocol);
    }

    @Bean
    public JwtPartitionValidationInterceptor jwtPartitionValidationInterceptor() {
        return new JwtPartitionValidationInterceptor();
    }
}
