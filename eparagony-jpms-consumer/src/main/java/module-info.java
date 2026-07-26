/**
 * A modular consumer of the SDK. Exists only so that the compiler enforces the export boundary that
 * {@code ADR-002} promises: if the SDK stops exporting something a consumer needs, or starts exposing
 * an internal type through an exported signature, this module fails to compile.
 */
module io.github.mgrtomaszzurawski.eparagony.jpms {
    requires io.github.mgrtomaszzurawski.eparagony;
}
