package com.swarmsight.authority.arena;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds an {@link HttpAgent} for a registered agent. The shared RestClient is
 * bounded by connect and read timeouts so a slow or hostile endpoint cannot hang
 * an assurance run; the per-agent URL and secret are applied per request.
 */
@Component
public class HttpAgentFactory {

    private final RestClient restClient;
    private final AgentEndpointValidator validator;

    public HttpAgentFactory(
            RestClient.Builder builder,
            AgentEndpointValidator validator,
            @Value("${swarmsight.agents.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${swarmsight.agents.read-timeout-ms:15000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.validator = validator;
    }

    public Agent forAgent(RegisteredAgent agent) {
        return new HttpAgent(restClient, agent.endpointUrl(), agent.callSecret(), agent.id(), validator);
    }
}
