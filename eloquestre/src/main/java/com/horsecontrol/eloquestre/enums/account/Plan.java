package com.horsecontrol.eloquestre.enums.account;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Plano de assinatura do usuário. No JSON vira "free" ou "standard". Só o servidor altera. */
public enum Plan {
    FREE("free"),
    STANDARD("standard");

    private final String json;

    Plan(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static Plan fromJson(String value) {
        for (Plan plan : values()) {
            if (plan.json.equals(value)) return plan;
        }
        throw new IllegalArgumentException("Plan inválido: " + value);
    }
}
