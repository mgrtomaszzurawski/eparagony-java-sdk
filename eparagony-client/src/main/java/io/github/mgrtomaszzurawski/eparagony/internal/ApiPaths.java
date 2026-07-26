package io.github.mgrtomaszzurawski.eparagony.internal;

/**
 * Every path the SDK addresses, in one place. The whole API is seven endpoints, so this is the
 * complete surface rather than a sample of it. Internal: never exported.
 */
public final class ApiPaths {

    /** {@code POST} — mints an access token. Lives on the auth host, not the API host. */
    public static final String AUTH_TOKEN = "/auth/token";

    /** {@code POST} — issues a document. */
    public static final String DOCUMENTS = "/documents";

    /** {@code GET} — current status of an issued document. */
    public static final String DOCUMENT_STATUS = "/documents/{documentToken}/status";

    /** {@code GET} — aggregate status of a document's asynchronous actions. */
    public static final String DOCUMENT_ACTIONS_STATUS = "/documents/{documentToken}/actions/status";

    /** {@code GET} — the signed JWS form of an issued document. */
    public static final String DOCUMENT_JWS = "/documents/{documentToken}/jws";

    /** {@code GET} — current status of a fiscal printer. */
    public static final String PRINTER_STATUS = "/printers/{fiscalDeviceUniqueNumber}/status";

    /** {@code GET} — daily fiscal reports for a printer. */
    public static final String PRINTER_DAILY_REPORTS = "/printers/{fiscalDeviceUniqueNumber}/reports/daily";

    private ApiPaths() {
    }
}
