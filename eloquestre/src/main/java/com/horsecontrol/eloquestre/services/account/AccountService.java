package com.horsecontrol.eloquestre.services.account;

import com.horsecontrol.eloquestre.models.chat.ChatMessage;
import com.horsecontrol.eloquestre.models.chat.Conversation;
import com.horsecontrol.eloquestre.models.feed.Comment;
import com.horsecontrol.eloquestre.models.feed.Post;
import com.horsecontrol.eloquestre.models.feed.PostLike;
import com.horsecontrol.eloquestre.models.jobs.Establishment;
import com.horsecontrol.eloquestre.models.jobs.Job;
import com.horsecontrol.eloquestre.models.jobs.JobApplication;
import com.horsecontrol.eloquestre.models.moderation.Block;
import com.horsecontrol.eloquestre.models.moderation.Report;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.models.push.PushToken;
import com.horsecontrol.eloquestre.models.reviews.Review;
import com.horsecontrol.eloquestre.models.services.ServiceOffer;
import com.horsecontrol.eloquestre.services.files.FileStorageService;
import com.horsecontrol.eloquestre.services.internal.Logger;
import com.horsecontrol.eloquestre.services.reviews.ReviewService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Exclusão de conta (exigida pelas lojas): apaga o usuário e tudo o que é dele em
 * todas as coleções, os arquivos enviados, e recalcula a nota de quem ele avaliou.
 */
@Service
public class AccountService {

    private final MongoTemplate mongo;
    private final FileStorageService files;
    private final ReviewService reviews;

    public AccountService(MongoTemplate mongo, FileStorageService files, ReviewService reviews) {
        this.mongo = mongo;
        this.files = files;
        this.reviews = reviews;
    }

    public void delete(String me) {
        // vagas (e candidaturas recebidas) e candidaturas feitas
        List<String> myJobs = ids(Query.query(Criteria.where("employerId").is(me)), Job.class);
        mongo.remove(Query.query(new Criteria().orOperator(Criteria.where("jobId").in(myJobs), Criteria.where("applicantId").is(me))), JobApplication.class);
        mongo.updateMulti(Query.query(Criteria.where("jobId").in(myJobs)), new org.springframework.data.mongodb.core.query.Update().set("jobId", null), Post.class);
        mongo.remove(Query.query(Criteria.where("employerId").is(me)), Job.class);
        mongo.remove(Query.query(Criteria.where("ownerId").is(me)), Establishment.class);
        mongo.remove(Query.query(Criteria.where("providerId").is(me)), ServiceOffer.class);

        // conversas e mensagens das quais participava
        List<String> convs = ids(Query.query(Criteria.where("members").is(me)), Conversation.class);
        mongo.remove(Query.query(Criteria.where("conversationId").in(convs)), ChatMessage.class);
        mongo.remove(Query.query(Criteria.where("members").is(me)), Conversation.class);

        // avaliações dadas e recebidas (recalcula quem eu avaliei)
        List<String> reviewed = mongo.find(Query.query(Criteria.where("reviewerId").is(me)), Review.class).stream().map(Review::getRevieweeId).toList();
        mongo.remove(Query.query(new Criteria().orOperator(Criteria.where("reviewerId").is(me), Criteria.where("revieweeId").is(me))), Review.class);
        reviews.recompute(reviewed);

        // publicações (com curtidas e comentários) e minhas curtidas/comentários
        List<String> posts = ids(Query.query(Criteria.where("authorId").is(me)), Post.class);
        mongo.remove(Query.query(new Criteria().orOperator(Criteria.where("postId").in(posts), Criteria.where("userId").is(me))), PostLike.class);
        mongo.remove(Query.query(new Criteria().orOperator(Criteria.where("postId").in(posts), Criteria.where("authorId").is(me))), Comment.class);
        mongo.remove(Query.query(Criteria.where("authorId").is(me)), Post.class);

        // moderação, push, arquivos e o próprio usuário
        mongo.remove(Query.query(new Criteria().orOperator(Criteria.where("blockerId").is(me), Criteria.where("blockedId").is(me))), Block.class);
        mongo.remove(Query.query(Criteria.where("reporterId").is(me)), Report.class);
        mongo.remove(Query.query(Criteria.where("userId").is(me)), PushToken.class);
        files.deleteAllOf(me);
        mongo.remove(Query.query(Criteria.where("id").is(me)), User.class);
        Logger.info("Conta excluída: " + me);
    }

    private <T> List<String> ids(Query q, Class<T> type) {
        q.fields().include("_id");
        return mongo.find(q, org.bson.Document.class, mongo.getCollectionName(type)).stream().map(d -> d.get("_id").toString()).toList();
    }
}
