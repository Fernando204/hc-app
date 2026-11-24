package com.horsecontrol.eloquestre.enums.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Raios permitidos pelo app (km). No JSON vira o número (10, 20, 50, 100, 200). */
public enum Radius {
    KM_10(10), KM_20(20), KM_50(50), KM_100(100), KM_200(200);

    private final int km;

    Radius(int km) {
        this.km = km;
    }

    @JsonValue
    public int km() {
        return km;
    }

    @JsonCreator
    public static Radius fromKm(int km) {
        for (Radius r : values()) {
            if (r.km == km) return r;
        }
        throw new IllegalArgumentException("Raio inválido: " + km + " (use 10, 20, 50, 100 ou 200)");
    }
}