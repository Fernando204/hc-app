package com.horsecontrol.eloquestre.services.settings;

import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.models.settings.AppSettings;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Regras de assinatura: quem pode publicar vagas. */
@Service
public class PlanService {

    private final MongoTemplate mongo;
    private final Users users;

    public PlanService(MongoTemplate mongo, Users users) {
        this.mongo = mongo;
        this.users = users;
    }

    public boolean paywallEnabled() {
        AppSettings s = mongo.findById(AppSettings.ID, AppSettings.class);
        return s != null && s.isPaywallEnabled();
    }

    /** !paywall ligado || plano ativo. */
    public boolean canPostJobs(String userId) {
        if (!paywallEnabled()) return true;
        User u = users.get(userId);
        return u != null && u.hasActivePlan();
    }

    public void ensureCanPostJobs(String userId) {
        if (!canPostJobs(userId)) throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Assinatura necessária para publicar vagas.");
    }
}
