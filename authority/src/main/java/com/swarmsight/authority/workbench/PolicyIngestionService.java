package com.swarmsight.authority.workbench;

import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.workbench.ProposeRequest.SourceInput;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Live policy ingestion: fetch a piece of legislation or policy from an
 * allowlisted public source, hash it for provenance, have Claude propose the
 * guards it implies and the date it comes into force, and stage that as a
 * <em>proposed</em> change on the Policy Workbench.
 *
 * <p>It never enacts. The fetched text is untrusted and the extraction is
 * fallible, so the output goes through the same human gate as any change: diff,
 * shadow replay, and a service owner's activation on a future effective date. The
 * source URI and a hash of its exact content are stored so an auditor can prove
 * what the candidate was derived from. The fetch is allowlisted to named hosts
 * (an SSRF guard); it will not reach internal addresses.
 */
@Service
public class PolicyIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestionService.class);

    private final RestClient http;
    private final PolicyWorkbench workbench;
    private final PolicyRepository policyRepository;
    private final PolicyChangeRepository changeRepository;
    private final PolicySourceStateRepository sourceState;
    private final PolicyChangeExtractor extractor;
    private final List<String> allowlist;
    private final int maxChars;
    private final String frameworkPolicyId;

    public PolicyIngestionService(
            RestClient.Builder builder,
            PolicyWorkbench workbench,
            PolicyRepository policyRepository,
            PolicyChangeRepository changeRepository,
            PolicySourceStateRepository sourceState,
            PolicyChangeExtractor extractor,
            @Value("${swarmsight.ingestion.allowlist:legislation.gov.uk,www.legislation.gov.uk}")
                    String allowlist,
            @Value("${swarmsight.ingestion.max-chars:60000}") int maxChars,
            @Value("${swarmsight.ingestion.framework-policy:HA-09}") String frameworkPolicyId) {
        this.http = builder.build();
        this.workbench = workbench;
        this.policyRepository = policyRepository;
        this.changeRepository = changeRepository;
        this.sourceState = sourceState;
        this.extractor = extractor;
        this.allowlist = Arrays.stream(allowlist.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        this.maxChars = maxChars;
        this.frameworkPolicyId = frameworkPolicyId;
    }

    /** Everything the human needs to review a freshly ingested candidate. */
    public record IngestionResult(
            String changeId,
            String policyId,
            String baseVersion,
            String proposedVersion,
            String sourceUri,
            String contentHash,
            String title,
            int fetchedChars,
            int addedGuards,
            Instant commencementDate,
            Instant suggestedEffectiveFrom,
            String extraction,
            String summary) {
    }

    /** The result of one poll of a watched source: whether it changed and, if so, what was staged. */
    public record PollOutcome(boolean changed, String contentHash, IngestionResult result) {
    }

    private record Fetched(byte[] bytes, String mediaType) {
    }

    /** Fetch the source and stage a proposed change unconditionally (the manual trigger). */
    public IngestionResult ingest(String url, String policyId) {
        URI uri = validate(url);
        return ingestBody(url, policyId, fetch(uri, url));
    }

    /**
     * The poller's path: fetch, and only stage a change when the content has moved
     * since we last saw it. An unchanged source costs one fetch and no Claude call.
     */
    public PollOutcome ingestIfChanged(String url, String policyId) {
        URI uri = validate(url);
        Fetched f = fetch(uri, url);
        String hash = sha256Hex(f.bytes());
        Optional<String> last = sourceState.lastHash(url);
        if (last.isPresent() && last.get().equals(hash)) {
            sourceState.recordChecked(url, policyId, Instant.now());
            return new PollOutcome(false, hash, null);
        }
        return new PollOutcome(true, hash, ingestBody(url, policyId, f));
    }

    private Fetched fetch(URI uri, String url) {
        var entity = http.get().uri(uri).retrieve().toEntity(byte[].class);
        byte[] body = entity.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("The source returned no content: " + url);
        }
        String mediaType = entity.getHeaders().getContentType() == null
                ? "" : entity.getHeaders().getContentType().toString();
        return new Fetched(body, mediaType);
    }

    private IngestionResult ingestBody(String url, String policyId, Fetched f) {
        URI uri = URI.create(url);
        return stage(url, uri.getHost(), fileNameOf(uri), policyId, f.bytes(), f.mediaType());
    }

    /**
     * Infer a policy change from a document the department already holds in its
     * SharePoint library (its own written policy), rather than a public URL. Runs
     * the same extract-propose-replay path and the same human gate; the source
     * locator records which SharePoint file the candidate was derived from.
     */
    public IngestionResult ingestDocument(
            String policyId, byte[] bytes, String mediaType, String fileName, String sourceLocator) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("The policy document has no content.");
        }
        return stage(sourceLocator, "SharePoint", fileName, policyId, bytes, mediaType);
    }

    /**
     * The shared staging core: hash the source for provenance, have Claude extract
     * the guards and commencement date, and stage a proposed change with an impact
     * preview and a pre-filled effective date. Enacts nothing.
     */
    private IngestionResult stage(
            String sourceLocator, String displayHost, String fileName,
            String policyId, byte[] rawBytes, String mediaType) {
        boolean isPdf = mediaType.toLowerCase().contains("pdf") || fileName.toLowerCase().endsWith(".pdf");
        // Cap text/HTML so the prompt stays bounded; never truncate a PDF's bytes.
        byte[] bytes = (!isPdf && rawBytes.length > maxChars)
                ? Arrays.copyOf(rawBytes, maxChars) : rawBytes;
        String contentHash = sha256Hex(rawBytes);

        PolicyVersion base = policyRepository.findVersions(policyId).stream().findFirst()
                .orElseGet(() -> bootstrapPolicy(policyId));

        PolicyChangeExtractor.PolicyExtraction ex = extractor.extract(bytes, mediaType, fileName);

        String proposedVersion = base.version() + "+ingest-" + contentHash.substring(0, 8);
        String title = ex.summary() == null || ex.summary().isBlank()
                ? "Ingested from " + displayHost : ex.summary();

        SourceDocument document =
                new SourceDocument(sourceLocator, contentHash.substring(0, 12), contentHash, title);
        SourceInput source = new SourceInput(document, ex.addGuards(), ex.removeGuards());
        ProposeRequest req = new ProposeRequest(policyId, base.version(), proposedVersion, List.of(source));

        PolicyChange change = workbench.propose(req);
        // Stage the impact preview immediately, so the human sees what would change.
        try {
            workbench.replay(change);
        } catch (Exception e) {
            log.warn("Shadow replay of ingested change {} failed: {}", change.id(), e.toString());
        }

        Instant now = Instant.now();
        Instant suggested = ex.commencementDate() != null && ex.commencementDate().isAfter(now)
                ? ex.commencementDate()
                : now.plus(90, ChronoUnit.DAYS);
        // Pre-fill the activation date with the commencement Claude read (or the
        // future default), so the service owner confirms rather than re-types it.
        changeRepository.setSuggestedEffectiveFrom(change.id(), suggested);
        // Remember this content so the poller treats it as seen, not a fresh change.
        sourceState.recordChanged(sourceLocator, policyId, contentHash, now);

        log.info("Ingested policy candidate {} for {} from {} ({} guards, extraction={})",
                proposedVersion, policyId, displayHost, ex.addGuards().size(),
                extractor.live() ? "claude" : "fallback");

        return new IngestionResult(
                change.id(), policyId, base.version(), proposedVersion, sourceLocator, contentHash, title,
                bytes.length, ex.addGuards().size(), ex.commencementDate(), suggested,
                extractor.live() ? "claude" : "fallback", ex.summary());
    }

    /**
     * Bootstrap a brand-new policy (e.g. a council's allocation scheme) from the
     * shared national framework: an empty-guard v0 that copies the framework's
     * actions and required inputs, so the council's actual rules can be layered on
     * as a reviewed change. The framework (HA-09 by default) is already approved,
     * so this enacts nothing council-specific; the ingested scheme still goes
     * through the human gate.
     */
    private PolicyVersion bootstrapPolicy(String policyId) {
        PolicyVersion framework = policyRepository.findVersions(frameworkPolicyId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No framework policy " + frameworkPolicyId + " to bootstrap " + policyId + " from."));
        PolicyVersion v0 = new PolicyVersion(
                policyId, "v0", Instant.parse("2000-01-01T00:00:00Z"),
                framework.requiredInputs(), framework.actionFloors(), List.of());
        policyRepository.insert(v0);
        log.info("Bootstrapped new policy {} v0 from the {} framework (no guards).",
                policyId, frameworkPolicyId);
        return v0;
    }

    private static String fileNameOf(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        int slash = path.lastIndexOf('/');
        return (slash < 0 ? path : path.substring(slash + 1)).toLowerCase();
    }

    /** Allow only https on an allowlisted host. The SSRF guard for the fetch. */
    private URI validate(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a valid URL: " + url);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only https sources are allowed.");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("The URL has no host.");
        }
        String h = host.toLowerCase();
        boolean ok = allowlist.stream().anyMatch(a -> h.equals(a) || h.endsWith("." + a));
        if (!ok) {
            throw new IllegalArgumentException(
                    "Host " + host + " is not on the policy-source allowlist " + allowlist + ".");
        }
        return uri;
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
