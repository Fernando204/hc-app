package com.horsecontrol.eloquestre.models.people;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;

/**
 * Cidade (município IBGE) embutida no documento. Guarda sempre o CENTRO da
 * cidade, nunca o endereço ou o GPS exato (regra de privacidade do app).
 * Reutilizável em Vaga, Serviço e Estabelecimento.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CityRef {

    private Integer cityIbge;

    @NotBlank
    private String cityName;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$")
    private String state;

    /** GeoJSON [longitude, latitude] — base das buscas por raio ($nearSphere / $geoWithin). */
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;

    public static CityRef of(Integer ibge, String name, String state, double lat, double lng) {
        return new CityRef(ibge, name, state, new GeoJsonPoint(lng, lat));
    }

    public double lat() {
        return location.getY();
    }

    public double lng() {
        return location.getX();
    }
}