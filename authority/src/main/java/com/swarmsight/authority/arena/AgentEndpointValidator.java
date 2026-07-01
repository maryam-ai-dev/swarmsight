package com.swarmsight.authority.arena;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The gate that makes "register any HTTP endpoint" safe. Authority makes
 * outbound calls to URLs that users supply, so without this an attacker could
 * point an agent at the cloud metadata service or an internal host and turn
 * Authority into an SSRF weapon.
 *
 * <p>Open to the public internet, internal blocked: the scheme must be http or
 * https, and every IP the host resolves to is rejected if it is loopback,
 * link-local (including the 169.254.169.254 metadata address), site-local
 * private, any-local, multicast, or IPv6 unique-local, unless the host is on the
 * allowlist. The internal Intelligence service is allowlisted so the seeded
 * agent works. Validation runs at registration and again before every call, so a
 * host that resolves to a public IP at registration but a private one at call
 * time (DNS rebinding) is still refused.
 */
@Component
public class AgentEndpointValidator {

    /** Thrown when an endpoint is not allowed. The message is safe to surface. */
    public static class InvalidEndpointException extends RuntimeException {
        public InvalidEndpointException(String message) {
            super(message);
        }
    }

    private final List<String> allowlist;

    public AgentEndpointValidator(
            @Value("${swarmsight.agents.endpoint-allowlist:intelligence,localhost,127.0.0.1}")
            String allowlist) {
        this.allowlist = Arrays.stream(allowlist.split(","))
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> !s.isBlank()).toList();
    }

    public void validate(String endpointUrl) {
        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (Exception e) {
            throw new InvalidEndpointException("Endpoint is not a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidEndpointException("Endpoint must be an http or https URL.");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidEndpointException("Endpoint must not contain credentials.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidEndpointException("Endpoint must have a host.");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (allowlist.contains(lowerHost)) {
            return;
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new InvalidEndpointException("Endpoint host could not be resolved.");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new InvalidEndpointException(
                        "Endpoint resolves to a non-routable or internal address, which is not allowed.");
            }
        }
    }

    private boolean isBlocked(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        // IPv6 unique-local (fc00::/7), not covered by isSiteLocalAddress.
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}
