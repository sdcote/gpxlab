package com.sdcote.gpxlab.sanitize;

import io.jenetics.jpx.GPX;
import org.w3c.dom.Document;

/** Transforms a GPX model and optionally its serialized XML document. */
public interface ISanitizer {
    /**
     * Sanitizes a GPX model without modifying the original.
     *
     * @param gpx the source model, not null
     * @return the sanitized model
     */
    GPX sanitize(GPX gpx);

    /**
     * Adjusts a serialized GPX document in place. Call after writing the
     * sanitized model to a DOM and before serializing that DOM to output.
     *
     * @param document the serialized GPX document, not null
     */
    default void postProcess(Document document) {
        java.util.Objects.requireNonNull(document, "document");
    }
}
