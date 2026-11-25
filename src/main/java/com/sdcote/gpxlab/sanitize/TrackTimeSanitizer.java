package com.sdcote.gpxlab.sanitize;

import io.jenetics.jpx.GPX;

import java.util.Objects;

/** Removes timestamps from track points, leaving routes and waypoints intact. */
public class TrackTimeSanitizer implements ISanitizer {
    /**
     * Removes the time from every point in every track segment.
     *
     * @param gpx the source model, not null
     * @return a copy without track point times
     */
    @Override
    public GPX sanitize(GPX gpx) {
        return Objects.requireNonNull(gpx, "gpx").toBuilder()
                .trackFilter()
                .map(track -> track.toBuilder()
                        .map(segment -> segment.toBuilder()
                                .map(point -> point.toBuilder().time(null).build())
                                .build())
                        .build())
                .build()
                .build();
    }
}