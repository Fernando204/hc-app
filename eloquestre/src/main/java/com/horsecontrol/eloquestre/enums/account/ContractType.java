package com.horsecontrol.eloquestre.enums.account;



import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Tipos de contrato aceitos pelo app. No JSON usa o texto em minúsculas (ex.: "prestacao_servico"). */
public enum ContractType {
    CLT("clt"),
    TEMPORARIO("temporario"),
    DIARIA("diaria"),
    PJ("pj"),
    PRESTACAO_SERVICO("prestacao_servico"),
    ESTAGIO("estagio");

    private final String json;

    ContractType(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static ContractType fromJson(String value) {
        for (ContractType type : values()) {
            if (type.json.equals(value)) return type;
        }
        throw new IllegalArgumentException("ContractType inválido: " + value);
    }
}
