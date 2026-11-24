package com.horsecontrol.eloquestre.services.moderation;

import com.horsecontrol.eloquestre.models.moderation.Report;
import com.horsecontrol.eloquestre.services.internal.Logger;
import com.horsecontrol.eloquestre.support.Body;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/** Denúncias. Repetida (mesmo alvo) = sucesso. 3 denúncias distintas ocultam a publicação. */
@Service
public class ReportService {

    public static final int HIDE_POST_AFTER = 3;

    private final MongoTemplate mongo;

    public ReportService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void report(String me, Map<String, Object> raw) {
        Body b = Body.of(raw);
        String target = b.oneOf("target_type", Report.TARGETS, true);
        String targetId = b.text("target_id", 60);
        String reason = b.text("reason", 60);
        String details = b.optText("details", 1000);
        try {
            mongo.insert(Report.builder().reporterId(me).targetType(target).targetId(targetId)
                    .reason(reason).details(details).createdAt(Instant.now()).build());
        } catch (DuplicateKeyException e) {
            return; // já denunciado por esta pessoa
        }
        Logger.warn("Denúncia: " + target + " " + targetId + " (" + reason + ")");

        if ("post".equals(target)) {
            long count = mongo.count(Query.query(Criteria.where("targetType").is("post").and("targetId").is(targetId)), Report.class);
            if (count >= HIDE_POST_AFTER) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(toId(targetId))), new Update().set("hidden", true), "posts");
            }
        }
    }

    private static Object toId(String id) {
        return org.bson.types.ObjectId.isValid(id) ? new org.bson.types.ObjectId(id) : id;
    }
}
