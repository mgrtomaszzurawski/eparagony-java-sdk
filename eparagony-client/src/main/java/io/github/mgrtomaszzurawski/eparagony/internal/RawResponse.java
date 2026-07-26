package io.github.mgrtomaszzurawski.eparagony.internal;

/**
 * A successful response, kept whole because the status code carries meaning of its own. Internal:
 * never exported.
 *
 * <p>{@code POST /documents} distinguishes {@code 200} from {@code 202} — the first means nothing was
 * ordered on the printer, the second that fiscalization is under way — so a decoder that saw only the
 * body could not tell the caller which happened.
 */
public record RawResponse(int statusCode, String body) {
}
