package com.sdcote.gpxlab.sanitize;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.WayPoint;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMResult;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SanitizerTest {
    @Test
    void removesMetadataAndOriginalCreatorAndPostProcessesSerializedDocument() throws Exception {
        GPX original = GPX.builder("private creator")
                .metadata(metadata -> metadata.name("private name").author("private author"))
                .addWayPoint(point -> point.lat(1).lon(2))
                .build();
        MetadataSanitizer sanitizer = new MetadataSanitizer();
        GPX sanitized = sanitizer.sanitize(original);
        assertTrue(sanitized.getMetadata().isEmpty());
        assertEquals("GPXLab", sanitized.getCreator());
        assertEquals(original.getWayPoints(), sanitized.getWayPoints());
        assertEquals("private creator", original.getCreator());
        assertTrue(original.getMetadata().isPresent());

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        DOMResult result = new DOMResult(document);
        GPX.Writer.of(GPX.Writer.Indent.NULL).write(sanitized, result);
        assertEquals("GPXLab", document.getDocumentElement().getAttribute("creator"));
        sanitizer.postProcess(document);
        assertFalse(document.getDocumentElement().hasAttribute("creator"));
        assertEquals("gpx", document.getDocumentElement().getNodeName());
        sanitizer.postProcess(document);
        assertFalse(document.getDocumentElement().hasAttribute("creator"));
    }

    @Test
    void removesOnlyTrackPointTimesAcrossTracksAndSegments() {
        Instant time = Instant.parse("2024-01-02T03:04:05Z");
        WayPoint point = WayPoint.builder().lat(1).lon(2).ele(10).time(time).name("point").build();
        GPX original = GPX.builder("creator")
                .metadata(metadata -> metadata.time(time))
                .addWayPoint(point)
                .addRoute(route -> route.addPoint(point))
                .addTrack(track -> track.name("track").addSegment(segment -> segment.addPoint(point))
                        .addSegment(segment -> segment.addPoint(point).addPoint(point.toBuilder().time(null).build())))
                .addTrack(track -> track.addSegment(segment -> segment.addPoint(point)))
                .build();
        GPX sanitized = new TrackTimeSanitizer().sanitize(original);
        assertEquals(original.getWayPoints(), sanitized.getWayPoints());
        assertEquals(original.getRoutes(), sanitized.getRoutes());
        assertEquals(original.getMetadata(), sanitized.getMetadata());
        assertEquals(original.getCreator(), sanitized.getCreator());
        assertEquals(2, sanitized.getTracks().size());
        assertEquals("track", sanitized.getTracks().getFirst().getName().orElseThrow());
        sanitized.tracks().flatMap(track -> track.segments())
                .flatMap(segment -> segment.points()).forEach(actual -> {
                    assertTrue(actual.getTime().isEmpty());
                    assertEquals(point.getLatitude(), actual.getLatitude());
                    assertEquals(point.getLongitude(), actual.getLongitude());
                    assertEquals(point.getElevation(), actual.getElevation());
                    assertEquals(point.getName(), actual.getName());
                });
        assertEquals(time, original.getTracks().getFirst().getSegments().getFirst().getPoints().getFirst().getTime().orElseThrow());
        assertEquals(sanitized, new TrackTimeSanitizer().sanitize(sanitized));
        assertEquals(GPX.builder().build(), new TrackTimeSanitizer().sanitize(GPX.builder().build()));
    }

    @Test
    void rejectsNullInputsAndMissingDocumentRoot() throws Exception {
        MetadataSanitizer metadata = new MetadataSanitizer();
        TrackTimeSanitizer track = new TrackTimeSanitizer();
        assertThrows(NullPointerException.class, () -> metadata.sanitize(null));
        assertThrows(NullPointerException.class, () -> track.sanitize(null));
        assertThrows(NullPointerException.class, () -> metadata.postProcess(null));
        assertThrows(NullPointerException.class, () -> track.postProcess(null));
        Document empty = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        assertThrows(IllegalArgumentException.class, () -> metadata.postProcess(empty));
        track.postProcess(empty);
    }
}