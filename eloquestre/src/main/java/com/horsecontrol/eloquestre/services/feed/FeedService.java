package com.horsecontrol.eloquestre.services.feed;

import com.horsecontrol.eloquestre.dto.jobs.JobViews;
import com.horsecontrol.eloquestre.models.feed.Comment;
import com.horsecontrol.eloquestre.models.feed.Post;
import com.horsecontrol.eloquestre.models.feed.PostLike;
import com.horsecontrol.eloquestre.models.people.CityRef;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.services.moderation.BlockService;
import com.horsecontrol.eloquestre.services.push.PushService;
import com.horsecontrol.eloquestre.support.Body;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Feed: publicações, curtidas e comentários (regras de docs/MIGRACAO-SPRING-BOOT.md). */
@Service
public class FeedService {

    public static final int PAGE_SIZE = 20;
    public static final int MAX_MEDIA = 10;
    public static final double MAX_VIDEO_SECONDS = 180;
    public static final int MAX_COMMENTS = 200;

    private final MongoTemplate mongo;
    private final Users users;
    private final BlockService blocks;
    private final PushService push;

    public FeedService(MongoTemplate mongo, Users users, BlockService blocks, PushService push) {
        this.mongo = mongo;
        this.users = users;
        this.blocks = blocks;
        this.push = push;
    }

    // ------------------------------------------------------------ publicações
    /** 20 por página, mais novas primeiro. Oculta as escondidas por denúncia (menos para o autor) e autores bloqueados. */
    public List<Map<String, Object>> feed(String me, String before, String author) {
        Set<String> hidden = blocks.hiddenFor(me);
        Criteria c = new Criteria().orOperator(Criteria.where("hidden").is(false), Criteria.where("authorId").is(me));
        List<Criteria> and = new ArrayList<>(List.of(c));
        if (!hidden.isEmpty()) and.add(Criteria.where("authorId").nin(hidden));
        if (author != null && !author.isBlank()) and.add(Criteria.where("authorId").is(author));
        if (before != null && !before.isBlank()) {
            try {
                and.add(Criteria.where("createdAt").lt(Instant.parse(before)));
            } catch (DateTimeParseException e) {
                throw Body.bad("before inválido (use ISO-8601).");
            }
        }
        List<Post> posts = mongo.find(Query.query(new Criteria().andOperator(and)).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(PAGE_SIZE), Post.class);
        return items(posts, me);
    }

    public Map<String, Object> get(String me, String id) {
        Post p = mongo.findById(id, Post.class);
        if (p == null || (p.isHidden() && !p.getAuthorId().equals(me)) || blocks.blockedBetween(me, p.getAuthorId()) && !p.getAuthorId().equals(me)) {
            throw Body.notFound("Publicação não encontrada.");
        }
        List<Map<String, Object>> out = items(List.of(p), me);
        if (out.isEmpty()) throw Body.notFound("Publicação não encontrada.");
        return out.get(0);
    }

    /** Cria a publicação. Só aceita mídias enviadas pelo próprio autor (media/<autor>/...). */
    @SuppressWarnings("unchecked")
    public String create(String me, Map<String, Object> raw) {
        Body b = Body.of(raw);
        String caption = b.optText("caption", 2200);
        Object rawMedia = b.raw("media");
        List<Post.Media> media = new ArrayList<>();
        if (rawMedia != null) {
            if (!(rawMedia instanceof Collection<?> list)) throw Body.bad("media deve ser uma lista.");
            if (list.size() > MAX_MEDIA) throw Body.bad("Até " + MAX_MEDIA + " fotos ou vídeos por publicação.");
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m0)) throw Body.bad("Item de mídia inválido.");
                Body m = Body.of((Map<String, Object>) m0);
                String path = m.text("path", 300);
                if (!path.startsWith("media/" + me + "/")) throw Body.bad("Mídia inválida: envie o arquivo antes (POST /uploads).");
                String type = m.oneOf("type", Set.of("image", "video"), true);
                Double duration = m.raw("duration") instanceof Number n ? n.doubleValue() : null;
                if ("video".equals(type) && duration != null && duration > MAX_VIDEO_SECONDS + 1) throw Body.bad("Vídeos podem ter até 180 segundos.");
                media.add(new Post.Media(path, type, m.optInt("width", 1, 20000), m.optInt("height", 1, 20000), duration));
            }
        }
        if (caption == null && media.isEmpty()) throw Body.bad("Escreva algo ou adicione uma foto/vídeo.");
        String jobId = b.optText("job_id", 40);
        Post p = mongo.insert(Post.builder().authorId(me).caption(caption != null ? caption : "").media(media)
                .jobId(jobId).hidden(false).createdAt(Instant.now()).build());
        return p.getId();
    }

    /** Só o autor apaga (leva junto curtidas e comentários). */
    public void delete(String me, String id) {
        Post p = mongo.findById(id, Post.class);
        if (p == null || !p.getAuthorId().equals(me)) throw Body.notFound("Publicação não encontrada.");
        mongo.remove(Query.query(Criteria.where("postId").is(id)), PostLike.class);
        mongo.remove(Query.query(Criteria.where("postId").is(id)), Comment.class);
        mongo.remove(p);
    }

    /** Alterna a curtida; devolve true se ficou curtido. */
    public boolean toggleLike(String me, String postId) {
        Post p = visible(me, postId);
        Query mine = Query.query(Criteria.where("postId").is(p.getId()).and("userId").is(me));
        if (mongo.remove(mine, PostLike.class).getDeletedCount() > 0) return false;
        try {
            mongo.insert(PostLike.builder().postId(p.getId()).userId(me).createdAt(Instant.now()).build());
        } catch (DuplicateKeyException ignored) {
            // dois toques simultâneos: já está curtido
        }
        return true;
    }

    // ------------------------------------------------------------ comentários
    /** Até 200, mais antigos primeiro; sem comentários de bloqueados. */
    public List<Map<String, Object>> comments(String me, String postId) {
        visible(me, postId);
        Set<String> hidden = blocks.hiddenFor(me);
        List<Comment> rows = mongo.find(Query.query(Criteria.where("postId").is(postId)).with(Sort.by("createdAt")).limit(MAX_COMMENTS), Comment.class)
                .stream().filter(c -> !hidden.contains(c.getAuthorId())).toList();
        Map<String, User> authors = users.byIds(rows.stream().map(Comment::getAuthorId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Comment c : rows) {
            User a = authors.get(c.getAuthorId());
            if (a == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("post_id", c.getPostId());
            m.put("author_id", c.getAuthorId());
            m.put("body", c.getBody());
            m.put("created_at", JobViews.iso(c.getCreatedAt()));
            m.put("author_first_name", Users.nz(a.getFirstName()));
            m.put("author_last_name", Users.nz(a.getLastName()));
            m.put("author_username", a.getUsername());
            m.put("author_avatar_url", a.getAvatarUrl());
            out.add(m);
        }
        return out;
    }

    public Comment addComment(String me, String postId, String body) {
        Post p = visible(me, postId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) throw Body.bad("Comentário vazio.");
        if (text.length() > 1000) throw Body.bad("Comentário muito longo (máximo de 1000 caracteres).");
        Comment c = mongo.insert(Comment.builder().postId(p.getId()).authorId(me).body(text).createdAt(Instant.now()).build());
        if (!p.getAuthorId().equals(me)) push.notify(p.getAuthorId(), "Novo comentário", push.nameOf(me) + ": " + text, "/post/" + p.getId());
        return c;
    }

    /** Apaga o comentário: o autor do comentário ou o autor da publicação. */
    public void deleteComment(String me, String id) {
        Comment c = mongo.findById(id, Comment.class);
        if (c == null) throw Body.notFound("Comentário não encontrado.");
        Post p = mongo.findById(c.getPostId(), Post.class);
        if (!c.getAuthorId().equals(me) && (p == null || !p.getAuthorId().equals(me))) throw Body.notFound("Comentário não encontrado.");
        mongo.remove(c);
    }

    public Post find(String id) {
        return mongo.findById(id, Post.class);
    }

    // ------------------------------------------------------------ auxiliares
    private Post visible(String me, String postId) {
        Post p = mongo.findById(postId, Post.class);
        if (p == null || (p.isHidden() && !p.getAuthorId().equals(me)) || (!p.getAuthorId().equals(me) && blocks.blockedBetween(me, p.getAuthorId()))) {
            throw Body.notFound("Publicação não encontrada.");
        }
        return p;
    }

    /** FeedItem (types.ts) com contagens e liked_by_me. */
    private List<Map<String, Object>> items(List<Post> posts, String me) {
        if (posts.isEmpty()) return List.of();
        List<String> ids = posts.stream().map(Post::getId).toList();
        Map<String, User> authors = users.byIds(posts.stream().map(Post::getAuthorId).toList());
        Map<String, Long> likes = count(ids, PostLike.class);
        Map<String, Long> comments = count(ids, Comment.class);
        Set<String> likedByMe = new HashSet<>();
        if (me != null) {
            for (PostLike l : mongo.find(Query.query(Criteria.where("postId").in(ids).and("userId").is(me)), PostLike.class)) likedByMe.add(l.getPostId());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Post p : posts) {
            User a = authors.get(p.getAuthorId());
            if (a == null) continue;
            CityRef c = a.getCity();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("author_id", p.getAuthorId());
            m.put("caption", p.getCaption());
            m.put("media", p.getMedia().stream().map(FeedService::media).toList());
            m.put("job_id", p.getJobId());
            m.put("like_count", likes.getOrDefault(p.getId(), 0L));
            m.put("comment_count", comments.getOrDefault(p.getId(), 0L));
            m.put("created_at", JobViews.iso(p.getCreatedAt()));
            m.put("author_first_name", Users.nz(a.getFirstName()));
            m.put("author_last_name", Users.nz(a.getLastName()));
            m.put("author_username", a.getUsername());
            m.put("author_avatar_url", a.getAvatarUrl());
            m.put("author_occupation_id", a.getOccupationId());
            m.put("author_city_name", c != null ? c.getCityName() : null);
            m.put("author_state", c != null ? c.getState() : null);
            m.put("author_verified", a.isVerified());
            m.put("liked_by_me", likedByMe.contains(p.getId()));
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> media(Post.Media x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", x.getPath());
        m.put("type", x.getType());
        if (x.getWidth() != null) m.put("width", x.getWidth());
        if (x.getHeight() != null) m.put("height", x.getHeight());
        if (x.getDuration() != null) m.put("duration", x.getDuration());
        return m;
    }

    private <T> Map<String, Long> count(List<String> postIds, Class<T> type) {
        Map<String, Long> out = new HashMap<>();
        Query q = Query.query(Criteria.where("postId").in(postIds));
        q.fields().include("postId");
        for (org.bson.Document d : mongo.find(q, org.bson.Document.class, mongo.getCollectionName(type))) {
            out.merge(String.valueOf(d.get("postId")), 1L, Long::sum);
        }
        return out;
    }
}
