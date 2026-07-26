package io.github.mgrtomaszzurawski.eparagony.core.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An OAuth 2 scope the authorization server can grant to a client. Each one unlocks a specific slice
 * of the API; a token is only useful for the endpoints its scopes cover.
 *
 * <p>Three of these are not granted by default and must be requested from eparagony.pl support:
 * {@link #DOCUMENT_ACTION_GET}, {@link #DOCUMENT_GET_JWS} and {@link #REPORT_FISCAL_GET}.
 */
public enum Scope {

    /** Issue documents and read their status. {@code POST /documents}, {@code GET /documents/{token}/status}. */
    DOCUMENT_CREATE("document_create"),

    /** Read the status of asynchronous document actions. {@code GET /documents/{token}/actions/status}. */
    DOCUMENT_ACTION_GET("document_action_get"),

    /** Read the signed JWS form of an issued document. {@code GET /documents/{token}/jws}. */
    DOCUMENT_GET_JWS("document_get_jws"),

    /** Read fiscal printer status. {@code GET /printers/{device}/status}. */
    PRINTER_GET("printer_get"),

    /** List daily fiscal reports. {@code GET /printers/{device}/reports/daily}. */
    REPORT_FISCAL_GET("report_fiscal_get"),

    /**
     * Alternative authority for reading document status. Undocumented in the specification's
     * {@code securitySchemes} but referenced by the status endpoint's security block, and accepted by
     * the live authorization server.
     */
    ECOMMERCE("ecommerce");

    /**
     * The wire separator between scopes. A space, per RFC 6749 — <em>not</em> the comma the published
     * specification's field description claims. This is not a cosmetic difference: the server treats a
     * comma-joined value as one unrecognised scope name, answers {@code HTTP 200} anyway, omits
     * {@code scope} from the response, and issues a token that every endpoint then rejects with an
     * opaque {@code 403}. See {@code KNOWN-SERVER-BEHAVIORS.md}.
     */
    static final String WIRE_SEPARATOR = " ";

    private final String wireValue;

    Scope(String wireValue) {
        this.wireValue = wireValue;
    }

    /** The literal value the authorization server expects, e.g. {@code document_create}. */
    public String wireValue() {
        return wireValue;
    }

    /** Renders scopes into a single {@code scope} parameter value, space-separated and deduplicated. */
    public static String toWireValue(Collection<Scope> scopes) {
        return scopes.stream()
                .map(Scope::wireValue)
                .distinct()
                .collect(Collectors.joining(WIRE_SEPARATOR));
    }

    /**
     * Parses a {@code scope} value returned by the authorization server. Unrecognised entries are
     * skipped rather than rejected: the server is free to add scopes without warning, and an SDK that
     * threw on an unknown grant would break the moment it did.
     */
    public static Set<Scope> parseWireValue(String wireValue) {
        Set<Scope> parsed = new LinkedHashSet<>();
        if (wireValue == null || wireValue.isBlank()) {
            return parsed;
        }
        for (String candidate : wireValue.trim().split("\\s+")) {
            Arrays.stream(values())
                    .filter(scope -> scope.wireValue.equals(candidate))
                    .findFirst()
                    .ifPresent(parsed::add);
        }
        return parsed;
    }
}
