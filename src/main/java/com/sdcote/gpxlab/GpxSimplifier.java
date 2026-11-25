package com.sdcote.gpxlab;

import io.jenetics.jpx.Bounds;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Length;
import io.jenetics.jpx.Metadata;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GpxSimplifier {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /**
     * Configurable parameters for GPX track cleanup and simplification.
     *
     * @param minStepMeters         Drop stationary GPS jitter below this distance (e.g., 1.5m; <= 0 disables)
     * @param rdpEpsilonMeters      Max perpendicular deviation from trail centerline (e.g., 2.5m)
     * @param elevationWindowMeters Radius in meters for distance-weighted elevation smoothing (e.g., 15.0m; <= 0 disables)
     */
    public record SimplificationConfig(
            double minStepMeters,
            double rdpEpsilonMeters,
            double elevationWindowMeters) {

        public static SimplificationConfig defaults() {
            return new SimplificationConfig(1.5, 2.5, 15.0);
        }
    }

    public static void simplifyGpx(
            Path inputPath,
            Path outputPath,
            double minStepMeters,
            double rdpEpsilonMeters,
            double elevationWindowMeters) throws IOException {
        simplifyGpx(inputPath, outputPath,
                new SimplificationConfig(minStepMeters, rdpEpsilonMeters, elevationWindowMeters));
    }

    public static void simplifyGpx(
            Path inputPath,
            Path outputPath,
            SimplificationConfig config) throws IOException {

        GPX raw = GPX.read(inputPath);
        int beforeCount = countPoints(raw);

        GPX simplified = raw.toBuilder()
                .extensions(null)
                .trackFilter()
                .map(track -> track.toBuilder()
                        .extensions(null)
                        .map(segment -> simplifySegment(segment, config))
                        .build())
                .build()
                .build();

        // Recompute accurate bounding box from simplified geometry
        List<WayPoint> remainingPoints = simplified.tracks()
                .flatMap(Track::segments)
                .flatMap(TrackSegment::points)
                .toList();

        GPX finalGpx = simplified.toBuilder()
                .metadata(simplified.getMetadata()
                        .map(m -> m.toBuilder().bounds(remainingPoints.stream().collect(Bounds.toBounds())).build())
                        .orElseGet(() -> Metadata.builder()
                                .bounds(remainingPoints.stream().collect(Bounds.toBounds()))
                                .build()))
                .build();

        GPX.write(finalGpx, outputPath);

        int afterCount = countPoints(finalGpx);
        System.out.printf("Simplified %s: %d -> %d points (%.1f%% reduction)%n",
                inputPath.getFileName(), beforeCount, afterCount,
                100.0 * (1.0 - (double) afterCount / Math.max(1, beforeCount)));
    }

    private static TrackSegment simplifySegment(TrackSegment segment, SimplificationConfig config) {
        List<WayPoint> rawPoints = segment.getPoints();
        if (rawPoints.size() <= 2) {
            return segment;
        }

        // 1. Filter out stationary GPS wander clusters
        List<WayPoint> movingPoints = filterStationaryPoints(rawPoints, config.minStepMeters());

        // 2. Smooth altitude spikes using a distance-weighted moving window
        List<WayPoint> smoothedPoints = smoothElevations(movingPoints, config.elevationWindowMeters());

        // 3. Run Ramer-Douglas-Peucker simplification on 2D trail centerline
        List<WayPoint> rdpPoints = ramerDouglasPeucker(smoothedPoints, config.rdpEpsilonMeters());

        // 4. Round coordinates (6 decimals) & elevation (1 decimal), strip timestamps/extensions
        List<WayPoint> cleanedPoints = rdpPoints.stream()
                .map(GpxSimplifier::normalizePoint)
                .toList();

        return TrackSegment.of(cleanedPoints);
    }

    private static List<WayPoint> filterStationaryPoints(List<WayPoint> points, double minStepMeters) {
        if (minStepMeters <= 0 || points.size() <= 2) {
            return points;
        }
        List<WayPoint> filtered = new ArrayList<>();
        WayPoint lastKept = points.get(0);
        filtered.add(lastKept);

        for (int i = 1; i < points.size() - 1; i++) {
            WayPoint current = points.get(i);
            double distMeters = lastKept.distance(current).doubleValue();
            if (distMeters >= minStepMeters) {
                filtered.add(current);
                lastKept = current;
            }
        }
        filtered.add(points.get(points.size() - 1));
        return filtered;
    }

    /**
     * Applies a triangular distance-weighted moving average to point elevations.
     * Points within {@code windowRadiusMeters} along the trail path contribute proportionally
     * to their proximity, eliminating GPS altitude spikes without flattening sustained climbs.
     */
    private static List<WayPoint> smoothElevations(List<WayPoint> points, double windowRadiusMeters) {
        if (windowRadiusMeters <= 0 || points.size() <= 2) {
            return points;
        }

        // Precompute cumulative distance along the track for O(1) path distance lookups
        double[] cumDist = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumDist[i] = cumDist[i - 1] + points.get(i - 1).distance(points.get(i)).doubleValue();
        }

        List<WayPoint> smoothed = new ArrayList<>(points.size());

        for (int i = 0; i < points.size(); i++) {
            WayPoint current = points.get(i);

            // Preserve exact endpoint elevations and skip points lacking <ele> tags
            if (i == 0 || i == points.size() - 1 || current.getElevation().isEmpty()) {
                smoothed.add(current);
                continue;
            }

            double weightedEleSum = 0.0;
            double totalWeight = 0.0;

            // Scan backward within radius
            for (int j = i; j >= 0; j--) {
                double d = cumDist[i] - cumDist[j];
                if (d > windowRadiusMeters) {
                    break;
                }
                var eleOpt = points.get(j).getElevation();
                if (eleOpt.isPresent()) {
                    double weight = 1.0 - (d / windowRadiusMeters);
                    weightedEleSum += eleOpt.get().doubleValue() * weight;
                    totalWeight += weight;
                }
            }

            // Scan forward within radius
            for (int j = i + 1; j < points.size(); j++) {
                double d = cumDist[j] - cumDist[i];
                if (d > windowRadiusMeters) {
                    break;
                }
                var eleOpt = points.get(j).getElevation();
                if (eleOpt.isPresent()) {
                    double weight = 1.0 - (d / windowRadiusMeters);
                    weightedEleSum += eleOpt.get().doubleValue() * weight;
                    totalWeight += weight;
                }
            }

            if (totalWeight > 0.0) {
                double smoothedEle = weightedEleSum / totalWeight;
                smoothed.add(current.toBuilder()
                        .ele(Length.of(smoothedEle, Length.Unit.METER))
                        .build());
            } else {
                smoothed.add(current);
            }
        }

        return smoothed;
    }

    private static List<WayPoint> ramerDouglasPeucker(List<WayPoint> points, double epsilonMeters) {
        if (epsilonMeters <= 0 || points.size() <= 2) {
            return points;
        }

        double maxDistance = 0.0;
        int index = 0;
        WayPoint start = points.get(0);
        WayPoint end = points.get(points.size() - 1);

        for (int i = 1; i < points.size() - 1; i++) {
            double distance = perpendicularDistanceMeters(points.get(i), start, end);
            if (distance > maxDistance) {
                index = i;
                maxDistance = distance;
            }
        }

        if (maxDistance > epsilonMeters) {
            List<WayPoint> left = ramerDouglasPeucker(points.subList(0, index + 1), epsilonMeters);
            List<WayPoint> right = ramerDouglasPeucker(points.subList(index, points.size()), epsilonMeters);

            List<WayPoint> result = new ArrayList<>(left.size() + right.size() - 1);
            result.addAll(left.subList(0, left.size() - 1));
            result.addAll(right);
            return result;
        } else {
            return List.of(start, end);
        }
    }

    private static double perpendicularDistanceMeters(WayPoint p, WayPoint a, WayPoint b) {
        double lat0 = Math.toRadians((a.getLatitude().doubleValue() + b.getLatitude().doubleValue()) / 2.0);
        double cosLat0 = Math.cos(lat0);

        double ax = Math.toRadians(a.getLongitude().doubleValue()) * cosLat0 * EARTH_RADIUS_M;
        double ay = Math.toRadians(a.getLatitude().doubleValue()) * EARTH_RADIUS_M;

        double bx = Math.toRadians(b.getLongitude().doubleValue()) * cosLat0 * EARTH_RADIUS_M;
        double by = Math.toRadians(b.getLatitude().doubleValue()) * EARTH_RADIUS_M;

        double px = Math.toRadians(p.getLongitude().doubleValue()) * cosLat0 * EARTH_RADIUS_M;
        double py = Math.toRadians(p.getLatitude().doubleValue()) * EARTH_RADIUS_M;

        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;

        if (lengthSq == 0.0) {
            return Math.hypot(px - ax, py - ay);
        }

        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));

        double projX = ax + t * dx;
        double projY = ay + t * dy;

        return Math.hypot(px - projX, py - projY);
    }

    private static WayPoint normalizePoint(WayPoint pt) {
        double lat = round(pt.getLatitude().doubleValue(), 6);
        double lon = round(pt.getLongitude().doubleValue(), 6);

        WayPoint.Builder builder = WayPoint.builder()
                .lat(lat)
                .lon(lon);

        pt.getElevation().ifPresent(ele ->
                builder.ele(Length.of(round(ele.doubleValue(), 1), Length.Unit.METER))
        );

        return builder.build();
    }

    private static double round(double value, int decimals) {
        return BigDecimal.valueOf(value)
                .setScale(decimals, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static int countPoints(GPX gpx) {
        return (int) gpx.tracks()
                .flatMap(Track::segments)
                .flatMap(TrackSegment::points)
                .count();
    }

    public static void main(String[] args) throws IOException {
        simplifyGpx(
                Path.of("raw-track.gpx"),
                Path.of("simplified-track.gpx"),
                1.5,  // Drop stationary jitter < 1.5m
                2.5,  // Preserve switchbacks within 2.5m of centerline
                15.0  // Smooth elevation noise across a 15m radius
        );
    }
}