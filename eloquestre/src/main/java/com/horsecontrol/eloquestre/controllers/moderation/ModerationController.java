package com.horsecontrol.eloquestre.controllers.moderation;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.services.moderation.BlockService;
import com.horsecontrol.eloquestre.services.moderation.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Bloqueios e denúncias. */
@RestController
public class ModerationController {

    private final BlockService blocks;
    private final ReportService reports;

    public ModerationController(BlockService blocks, ReportService reports) {
        this.blocks = blocks;
        this.reports = reports;
    }

    @PutMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String userId) {
        blocks.block(me, userId);
    }

    @DeleteMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String userId) {
        blocks.unblock(me, userId);
    }

    @GetMapping("/me/blocks")
    public List<Map<String, Object>> myBlocks(@RequestAttribute(JwtAuthFilter.USER_ID) String me) {
        return blocks.mine(me);
    }

    /** POST /reports {target_type, target_id, reason, details} */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody Map<String, Object> body) {
        reports.report(me, body);
    }
}
