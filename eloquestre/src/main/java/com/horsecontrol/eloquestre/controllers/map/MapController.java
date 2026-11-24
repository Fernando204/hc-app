package com.horsecontrol.eloquestre.controllers.map;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.services.map.MapService;
import com.horsecontrol.eloquestre.support.Body;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class MapController {

    private static final Set<String> KINDS = Set.of("pessoa", "servico", "empresa");

    private final MapService map;

    public MapController(MapService map) {
        this.map = map;
    }

    /** GET /map/pins?south&west&north&east&kinds=pessoa,servico,empresa&occupation */
    @GetMapping("/map/pins")
    public List<Map<String, Object>> pins(@RequestAttribute(JwtAuthFilter.USER_ID) String me,
                                          @RequestParam double south, @RequestParam double west,
                                          @RequestParam double north, @RequestParam double east,
                                          @RequestParam(defaultValue = "pessoa,servico,empresa") String kinds,
                                          @RequestParam(required = false) String occupation) {
        if (south > north) throw Body.bad("south deve ser menor que north.");
        Set<String> k = Arrays.stream(kinds.split(",")).map(String::trim).filter(KINDS::contains).collect(Collectors.toSet());
        return map.pins(me, new MapService.Bounds(south, west, north, east), k, occupation);
    }
}
