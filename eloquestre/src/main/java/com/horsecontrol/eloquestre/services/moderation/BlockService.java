package com.horsecontrol.eloquestre.services.moderation;

import com.horsecontrol.eloquestre.models.moderation.Block;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.support.Body;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bloqueios. Use {@link #hiddenFor} para esconder pessoas bloqueadas (nos dois sentidos) em qualquer listagem. */
@Service
public class BlockService {

    private final MongoTemplate mongo;
    private final Users users;

    public BlockService(MongoTemplate mongo, Users users) {
        this.mongo = mongo;
        this.users = users;
    }

    /** Ids que `me` bloqueou ou que bloquearam `me`. */
    public Set<String> hiddenFor(String me) {
        Set<String> out = new HashSet<>();
        if (me == null) return out;
        for (Block b : mongo.find(Query.query(new Criteria().orOperator(
                Criteria.where("blockerId").is(me), Criteria.where("blockedId").is(me))), Block.class)) {
            out.add(b.getBlockerId().equals(me) ? b.getBlockedId() : b.getBlockerId());
        }
        return out;
    }

    public boolean blockedBetween(String a, String b) {
        return mongo.exists(Query.query(new Criteria().orOperator(
                Criteria.where("blockerId").is(a).and("blockedId").is(b),
                Criteria.where("blockerId").is(b).and("blockedId").is(a))), Block.class);
    }

    /** Lança 403 "blocked" se houver bloqueio entre os dois. */
    public void ensureNotBlocked(String a, String b) {
        if (blockedBetween(a, b)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "blocked");
    }

    public void block(String me, String other) {
        if (other == null || other.equals(me)) throw Body.bad("invalid_peer");
        if (users.get(other) == null) throw Body.notFound("peer_not_found");
        try {
            mongo.insert(Block.builder().blockerId(me).blockedId(other).createdAt(Instant.now()).build());
        } catch (DuplicateKeyException ignored) {
            // já bloqueado: idempotente
        }
    }

    public void unblock(String me, String other) {
        mongo.remove(Query.query(Criteria.where("blockerId").is(me).and("blockedId").is(other)), Block.class);
    }

    /** Quem eu bloqueei: [{blocked_id, first_name, last_name, username}]. */
    public List<Map<String, Object>> mine(String me) {
        List<Block> blocks = mongo.find(Query.query(Criteria.where("blockerId").is(me)).with(Sort.by(Sort.Direction.DESC, "createdAt")), Block.class);
        Map<String, User> byId = users.byIds(blocks.stream().map(Block::getBlockedId).toList());
        return blocks.stream().filter(b -> byId.containsKey(b.getBlockedId())).map(b -> {
            User u = byId.get(b.getBlockedId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("blocked_id", u.getId());
            m.put("first_name", Users.nz(u.getFirstName()));
            m.put("last_name", Users.nz(u.getLastName()));
            m.put("username", u.getUsername());
            return m;
        }).toList();
    }
}
