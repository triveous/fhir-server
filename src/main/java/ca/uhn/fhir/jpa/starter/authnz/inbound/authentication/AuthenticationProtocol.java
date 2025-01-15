package ca.uhn.fhir.jpa.starter.authnz.inbound.authentication;

import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.UserSessionDetail;
import ca.uhn.fhir.rest.api.server.RequestDetails;

import java.util.ArrayList;
import java.util.List;

public interface AuthenticationProtocol {

    boolean canAuthenticate(RequestDetails requestDetails);
    UserSessionDetail authenticate(RequestDetails requestDetails);
    String name();

    class Registry {
        private final List<AuthenticationProtocol> protocols = new ArrayList<>();

        public Registry(OIDCAuthenticationProtocol oAuth2) {
            protocols.add(oAuth2);
        }

        List<AuthenticationProtocol> getAll() {
            return protocols;
        }
    }
}
