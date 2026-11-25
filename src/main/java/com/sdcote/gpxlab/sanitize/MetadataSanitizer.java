package com.sdcote.gpxlab.sanitize;

import io.jenetics.jpx.GPX;
import org.w3c.dom.Document;

import java.util.Objects;

/** Removes document metadata and the original creator. */
public class MetadataSanitizer implements ISanitizer {
    private static final String REPLACEMENT_CREATOR = "GPXLab";

    /**
     * Removes metadata and replaces the creator with a neutral value because
     * JPX requires a non-null creator.
     *
     * @param gpx the source model, not null
     * @return a copy with no metadata and a replacement creator
     */
    @Override
    public GPX sanitize(GPX gpx) {
        return Objects.requireNonNull(gpx, "gpx").toBuilder()
                .metadata((io.jenetics.jpx.Metadata) null)
                .creator(REPLACEMENT_CREATOR)
                .build();
    }

    /**
     * Removes the creator attribute from the serialized root in place. The
     * resulting document does not conform to the GPX 1.1 creator requirement.
     *
     * @param document the serialized GPX document, not null
     * @throws IllegalArgumentException if the document has no root element
     */
    @Override
    public void postProcess(Document document) {
        Objects.requireNonNull(document, "document");
        if (document.getDocumentElement() == null) {
            throw new IllegalArgumentException("Document has no root element");
        }
        document.getDocumentElement().removeAttribute("creator");
    }
}