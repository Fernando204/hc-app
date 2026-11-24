package com.horsecontrol.eloquestre.enums.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Tipo de conta do ELOequestre. No JSON vira "professional" ou "employer". */
public enum AccountType {
    PROFESSIONAL("professional"),
    EMPLOYER("employer");

    private final String json;

    AccountType(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static AccountType fromJson(String value) {
        for (AccountType type : values()) {
            if (type.json.equals(value)) return type;
        }
        throw new IllegalArgumentException("AccountType inválido: " + value);
    }
}