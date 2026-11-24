package com.horsecontrol.eloquestre.services.files;

import com.horsecontrol.eloquestre.services.internal.Logger;
import com.horsecontrol.eloquestre.support.Body;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Arquivos enviados (foto de perfil e mídias do feed) gravados em disco, em
 * {app.storage.dir}/{bucket}/{userId}/{uuid}.{ext}, e servidos em GET /files/**.
 * O banco guarda só o caminho relativo ("media/<user>/<arquivo>.jpg"); o app monta a URL.
 *
 * Produção com mais de uma instância (ou disco efêmero): trocar por S3/Cloudinary
 * mantendo a mesma interface (store / deleteAllOf).
 */
@Service
public class FileStorageService {

    public static final Set<String> BUCKETS = Set.of("avatars", "media");
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    private static final Map<String, String> EXT = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/heic", "heic",
            "video/mp4", "mp4", "video/quicktime", "mov");

    private final Path root;

    public FileStorageService(@Value("${app.storage.dir:uploads}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Logger.info("Arquivos enviados em: " + root);
    }

    public Path root() {
        return root;
    }

    /** Grava e devolve o caminho relativo. Só imagens no bucket "avatars". */
    public String store(String userId, String bucket, MultipartFile file) {
        if (!BUCKETS.contains(bucket)) throw Body.bad("bucket inválido (use avatars ou media).");
        if (file == null || file.isEmpty()) throw Body.bad("Arquivo vazio.");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String ext = EXT.get(type);
        if (ext == null) throw Body.bad("Tipo de arquivo não aceito: " + type);
        boolean video = type.startsWith("video/");
        if (video && bucket.equals("avatars")) throw Body.bad("A foto de perfil precisa ser uma imagem.");
        if (file.getSize() > (video ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES)) {
            throw Body.bad(video ? "O vídeo passa de 100 MB." : "A imagem passa de 10 MB.");
        }
        String rel = bucket + "/" + userId + "/" + UUID.randomUUID() + "." + ext;
        Path dest = root.resolve(rel).normalize();
        if (!dest.startsWith(root)) throw Body.bad("Caminho inválido.");
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar o arquivo", e);
        }
        return rel;
    }

    /** Apaga todos os arquivos do usuário (exclusão de conta). */
    public void deleteAllOf(String userId) {
        for (String bucket : BUCKETS) {
            try {
                FileSystemUtils.deleteRecursively(root.resolve(bucket).resolve(userId).normalize());
            } catch (IOException e) {
                Logger.warn("Não foi possível apagar arquivos de " + userId + ": " + e.getMessage());
            }
        }
    }
}
