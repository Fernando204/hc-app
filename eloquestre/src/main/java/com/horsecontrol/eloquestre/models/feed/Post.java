package com.horsecontrol.eloquestre.models.feed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Publicação do feed (texto + até 10 fotos/vídeos). `hidden` = ocultada por denúncias. */
@Document(collection = "posts")
@CompoundIndexes({
        @CompoundIndex(name = "created_idx", def = "{ 'createdAt': -1 }"),
        @CompoundIndex(name = "author_created_idx", def = "{ 'authorId': 1, 'createdAt': -1 }")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    private String id;
    private String authorId;
    private String caption;
    @Builder.Default
    private List<Media> media = new ArrayList<>();
    private String jobId;
    private boolean hidden;
    private Instant createdAt;

    /** Caminho relativo do arquivo enviado (ver FileStorageService). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Media {
        private String path;
        /** image | video */
        private String type;
        private Integer width;
        private Integer height;
        private Double duration;
    }
}
