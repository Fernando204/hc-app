package com.horsecontrol.eloquestre.controllers.feed;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.services.feed.FeedService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Feed: publicações, curtidas e comentários. */
@RestController
public class FeedController {

    private final FeedService feed;

    public FeedController(FeedService feed) {
        this.feed = feed;
    }

    /** GET /posts?before&author */
    @GetMapping("/posts")
    public List<Map<String, Object>> list(@RequestAttribute(JwtAuthFilter.USER_ID) String me,
                                          @RequestParam(required = false) String before, @RequestParam(required = false) String author) {
        return feed.feed(me, before, author);
    }

    @GetMapping("/posts/{id}")
    public Map<String, Object> get(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return feed.get(me, id);
    }

    /** POST /posts {caption, media:[{path,type,width,height,duration}], job_id} → {id} */
    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody Map<String, Object> body) {
        return Map.of("id", feed.create(me, body));
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        feed.delete(me, id);
    }

    /** POST /posts/{id}/like → {liked} */
    @PostMapping("/posts/{id}/like")
    public Map<String, Boolean> like(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return Map.of("liked", feed.toggleLike(me, id));
    }

    @GetMapping("/posts/{id}/comments")
    public List<Map<String, Object>> comments(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return feed.comments(me, id);
    }

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> comment(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id, @RequestBody Map<String, String> body) {
        return Map.of("id", feed.addComment(me, id, body.get("body")).getId());
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        feed.deleteComment(me, id);
    }
}
