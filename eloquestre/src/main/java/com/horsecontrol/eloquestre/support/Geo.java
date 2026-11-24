package com.horsecontrol.eloquestre.support;

import com.horsecontrol.eloquestre.models.people.CityRef;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.query.Criteria;

import java.text.Normalizer;
import java.util.Locale;

/** Distâncias (Haversine), filtro por raio no Mongo e normalização de texto para busca. */
public final class Geo {

    private Geo() {
    }

    /** km entre o ponto e o centro da cidade; infinito se não houver cidade. */
    public static double km(CityRef c, double lat, double lng) {
        if (c == null || c.getLocation() == null) return Double.POSITIVE_INFINITY;
        return km(c.lat(), c.lng(), lat, lng);
    }

    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6371.0 * Math.asin(Math.sqrt(a));
    }

    /** Critério "a cidade do documento está a até `radiusKm` do ponto". */
    public static Criteria within(String field, double lat, double lng, double radiusKm) {
        return Criteria.where(field).withinSphere(new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS)));
    }

    /** Sem acentos e em minúsculas (busca "tratador" acha "Tratador", "pião" acha "piao"). */
    public static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String query, String... fields) {
        String q = norm(query).strip();
        if (q.isEmpty()) return true;
        for (String f : fields) if (norm(f).contains(q)) return true;
        return false;
    }
}
