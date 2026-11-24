package com.horsecontrol.eloquestre.controllers.offers;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.services.offers.ServiceOfferService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Serviços de prestadores. */
@RestController
public class ServiceOfferController {

    private final ServiceOfferService services;

    public ServiceOfferController(ServiceOfferService services) {
        this.services = services;
    }

    /** GET /services?lat&lng&occupation&page */
    @GetMapping("/services")
    public List<Map<String, Object>> search(@RequestAttribute(JwtAuthFilter.USER_ID) String me,
                                            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
                                            @RequestParam(required = false) String occupation, @RequestParam(defaultValue = "0") int page) {
        return services.search(me, new ServiceOfferService.ServiceSearch(lat, lng, occupation, page));
    }

    @GetMapping("/profiles/{id}/services")
    public List<Map<String, Object>> of(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return services.of(me, id);
    }

    @GetMapping("/services/{id}")
    public Map<String, Object> get(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return services.get(me, id);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody Map<String, Object> body) {
        return services.save(me, null, body);
    }

    @PutMapping("/services/{id}")
    public Map<String, Object> update(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return services.save(me, id, body);
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        services.delete(me, id);
    }
}
