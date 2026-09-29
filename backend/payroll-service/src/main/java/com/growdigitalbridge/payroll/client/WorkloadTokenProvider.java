package com.growdigitalbridge.payroll.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.growdigitalbridge.payroll.config.DocumentServiceClientProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Obtains a workload access token for the {@code payroll-service} OAuth2 client-credentials
 * registration (SECURITY.md's already-documented "workload identity or OAuth2 client
 * credentials" mechanism; PAYROLL_REQUIREMENTS.md Section N). Issues a standard RFC 6749
 * client-credentials grant request directly (form-encoded POST, {@code access_token} response
 * field) rather than pulling in a full OAuth2-client framework, since this service already has
 * everything else it needs via plain {@link RestClient} - the wire protocol is the same either
 * way. Fails closed with {@link IllegalStateException} when {@code gdb.clients.document-service.
 * token-uri} is not configured, exactly like {@link com.growdigitalbridge.payroll.config.SecurityConfig}'s
 * {@code JwtDecoder} bean stays absent until an OIDC issuer is supplied - never a bypass.
 */
@Component
public class WorkloadTokenProvider {

    private final RestClient tokenRestClient;
    private final DocumentServiceClientProperties properties;

    public WorkloadTokenProvider(RestClient tokenRestClient, DocumentServiceClientProperties properties) {
        this.tokenRestClient = tokenRestClient;
        this.properties = properties;
    }

    public String fetchToken() {
        if (properties.tokenUri() == null || properties.tokenUri().isBlank()) {
            throw new IllegalStateException(
                    "Document Service workload credentials are not configured (gdb.clients.document-service.token-uri).");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response = tokenRestClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Document Service workload token endpoint returned no access token.");
        }
        return response.accessToken();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) { }
}
