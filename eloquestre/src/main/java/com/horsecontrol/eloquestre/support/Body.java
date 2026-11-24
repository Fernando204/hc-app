package com.horsecontrol.eloquestre.support;

import com.horsecontrol.eloquestre.models.people.CityRef;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Leitura validada de corpos JSON recebidos como Map (chaves em snake_case, como o app envia).
 * Todas as falhas viram 400 com uma mensagem em português.
 */
public final class Body {

    private final Map<String, Object> map;

    private Body(Map<String, Object> map) {
        this.map = map != null ? map : Map.of();
    }

    public static Body of(Map<String, Object> map) {
        return new Body(map);
    }

    public boolean has(String key) {
        return map.containsKey(key);
    }

    public Object raw(String key) {
        return map.get(key);
    }

    /** Texto obrigatório (não vazio), até `max` caracteres. */
    public String text(String key, int max) {
        String s = optText(key, max);
        if (s == null) throw bad(key + " é obrigatório.");
        return s;
    }

    /** Texto opcional: null/vazio viram null. */
    public String optText(String key, int max) {
        Object v = map.get(key);
        if (v == null) return null;
        String s = v.toString().strip();
        if (s.length() > max) throw bad(key + ": máximo de " + max + " caracteres.");
        return s.isEmpty() ? null : s;
    }

    public String oneOf(String key, Set<String> allowed, boolean required) {
        String s = optText(key, 60);
        if (s == null) {
            if (required) throw bad(key + " é obrigatório.");
            return null;
        }
        if (!allowed.contains(s)) throw bad(key + " inválido: " + s);
        return s;
    }

    public boolean bool(String key, boolean ifAbsent) {
        Object v = map.get(key);
        if (v == null) return ifAbsent;
        if (v instanceof Boolean b) return b;
        throw bad(key + " deve ser true ou false.");
    }

    public Integer optInt(String key, int min, int max) {
        Object v = map.get(key);
        if (v == null) return null;
        if (!(v instanceof Number n)) throw bad(key + " deve ser um número.");
        int i = n.intValue();
        if (i < min || i > max) throw bad(key + " deve estar entre " + min + " e " + max + ".");
        return i;
    }

    public Double optMoney(String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (!(v instanceof Number n)) throw bad(key + " deve ser um número.");
        double d = n.doubleValue();
        if (d < 0 || d > 10_000_000) throw bad(key + " fora do intervalo permitido.");
        return d;
    }

    /** Data "AAAA-MM-DD" opcional. */
    public String optDate(String key) {
        String s = optText(key, 10);
        if (s == null) return null;
        try {
            return LocalDate.parse(s).toString();
        } catch (DateTimeParseException e) {
            throw bad(key + " deve estar no formato AAAA-MM-DD.");
        }
    }

    public List<String> strings(String key, int maxItems, int maxLen) {
        Object v = map.get(key);
        if (v == null) return new ArrayList<>();
        if (!(v instanceof Collection<?> c)) throw bad(key + " deve ser uma lista.");
        if (c.size() > maxItems) throw bad(key + ": máximo de " + maxItems + " itens.");
        List<String> out = new ArrayList<>();
        for (Object o : c) {
            if (o == null || o.toString().isBlank()) continue;
            String s = o.toString().strip();
            if (s.length() > maxLen) throw bad(key + ": cada item com até " + maxLen + " caracteres.");
            out.add(s);
        }
        return out;
    }

    /**
     * Cidade (centro do município) a partir de city_ibge, city_name, state, lat, lng.
     * Campos ausentes usam os de `current` (atualização parcial).
     */
    public CityRef city(CityRef current, boolean required) {
        boolean any = has("lat") || has("lng") || has("city_name") || has("state") || has("city_ibge");
        if (!any) {
            if (required && current == null) throw bad("Informe a cidade.");
            return current;
        }
        Object lat = has("lat") ? raw("lat") : current != null && current.getLocation() != null ? current.lat() : null;
        Object lng = has("lng") ? raw("lng") : current != null && current.getLocation() != null ? current.lng() : null;
        Object name = has("city_name") ? raw("city_name") : current != null ? current.getCityName() : null;
        Object state = has("state") ? raw("state") : current != null ? current.getState() : null;
        Object ibge = has("city_ibge") ? raw("city_ibge") : current != null ? current.getCityIbge() : null;
        if (!(lat instanceof Number la) || !(lng instanceof Number lo) || name == null || name.toString().isBlank()
                || state == null || !state.toString().matches("^[A-Z]{2}$")) {
            throw bad("Cidade inválida.");
        }
        if (Math.abs(la.doubleValue()) > 90 || Math.abs(lo.doubleValue()) > 180) throw bad("Coordenadas inválidas.");
        return CityRef.of(ibge instanceof Number n ? n.intValue() : null, name.toString().strip(), state.toString(), la.doubleValue(), lo.doubleValue());
    }

    public static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
