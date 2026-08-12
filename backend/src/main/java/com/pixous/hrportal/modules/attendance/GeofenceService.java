package com.pixous.hrportal.modules.attendance;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Haversine distance + geofence containment used for GPS attendance validation. */
@Service
public class GeofenceService {

    private static final double EARTH_RADIUS_METRES = 6_371_000.0;

    /** Great-circle distance between two lat/lng points, in metres. */
    public double distanceMetres(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METRES * c;
    }

    public boolean isWithin(BigDecimal punchLat, BigDecimal punchLng,
                            BigDecimal centreLat, BigDecimal centreLng,
                            int radiusMetres) {
        if (punchLat == null || punchLng == null || centreLat == null || centreLng == null) {
            return false;
        }
        double distance = distanceMetres(
                punchLat.doubleValue(), punchLng.doubleValue(),
                centreLat.doubleValue(), centreLng.doubleValue());
        return distance <= radiusMetres;
    }
}
