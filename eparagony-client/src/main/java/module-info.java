/**
 * The eparagony.pl Documents SDK. Only {@code core.*} (auth, config, errors, retry, shared value
 * types, webhook verification) and the {@code domain.*} facades are exported; the {@code internal}
 * transport and the generated {@code rest.model} types are never exposed.
 */
module io.github.mgrtomaszzurawski.eparagony {

    requires java.net.http;
    // Internal-only dependencies (never re-exported): transport JSON and the generated Layer-1 models.
    requires com.fasterxml.jackson.databind;
    // Datatype modules the generated models need: date-time properties and JsonNullable fields.
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.openapitools.jackson.nullable;
    requires io.github.mgrtomaszzurawski.eparagony.rest.models;

    // Entry point.
    exports io.github.mgrtomaszzurawski.eparagony;

    // Core (public shared surface).
    exports io.github.mgrtomaszzurawski.eparagony.core.auth;
    exports io.github.mgrtomaszzurawski.eparagony.core.config;
    exports io.github.mgrtomaszzurawski.eparagony.core.error;
    exports io.github.mgrtomaszzurawski.eparagony.core.model;
    exports io.github.mgrtomaszzurawski.eparagony.core.retry;
    exports io.github.mgrtomaszzurawski.eparagony.core.webhook;

    // Domain facades.
    exports io.github.mgrtomaszzurawski.eparagony.domain.documents;
    exports io.github.mgrtomaszzurawski.eparagony.domain.documents.model;
    exports io.github.mgrtomaszzurawski.eparagony.domain.printers;
    exports io.github.mgrtomaszzurawski.eparagony.domain.printers.model;
}
