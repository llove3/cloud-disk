package com.example.clouddisk.ai;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.mapper.FileMapper;
import tools.jackson.databind.JsonNode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

@Service
public class AiSearchService {
    private static final String INDEX = "/cloud_disk_chunks";
    private final RestClient es;
    private final EmbeddingModel embeddings;
    private final FileMapper files;
    private volatile boolean indexReady;

    public record Source(int number, Long fileId, String fileName, int version, String snippet) {}

    public AiSearchService(@Value("${ai.elasticsearch.url}") String url,
                           EmbeddingModel embeddings, FileMapper files) {
        this.es = RestClient.builder().baseUrl(url).build();
        this.embeddings = embeddings;
        this.files = files;
    }

    private synchronized void ensureIndex() {
        if (indexReady) return;
        try {
            es.head().uri(INDEX).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) throw e;
            es.put().uri(INDEX).body(Map.of("mappings", Map.of("properties", Map.of(
                    "ownerId", Map.of("type", "long"),
                    "fileId", Map.of("type", "long"),
                    "generation", Map.of("type", "long"),
                    "version", Map.of("type", "integer"),
                    "fileName", Map.of("type", "keyword"),
                    "content", Map.of("type", "text"),
                    "vector", Map.of("type", "dense_vector", "dims", 384, "index", true, "similarity", "cosine")
            )))).retrieve().toBodilessEntity();
        }
        indexReady = true;
    }

    public void deleteFile(Long fileId) {
        ensureIndex();
        es.post().uri(INDEX + "/_delete_by_query?refresh=true&conflicts=proceed")
                .body(Map.of("query", Map.of("term", Map.of("fileId", fileId))))
                .retrieve().toBodilessEntity();
    }

    public void index(FileInfo file, long generation, List<String> chunks) {
        ensureIndex();
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            Map<String, Object> document = new HashMap<>();
            document.put("ownerId", file.getUserId());
            document.put("fileId", file.getId());
            document.put("generation", generation);
            document.put("version", file.getVersion());
            document.put("fileName", file.getFileName());
            document.put("content", content);
            document.put("vector", embeddings.embed(content));
            es.put().uri(INDEX + "/_doc/{id}", file.getId() + ":" + generation + ":" + i)
                    .body(document).retrieve().toBodilessEntity();
        }
        es.post().uri(INDEX + "/_refresh").retrieve().toBodilessEntity();
    }

    public List<Source> search(Long userId, String query, Long onlyFileId, int limit) {
        if (query == null || query.isBlank()) return List.of();
        ensureIndex();
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("ownerId", userId)));
        if (onlyFileId != null) filters.add(Map.of("term", Map.of("fileId", onlyFileId)));
        Map<String, Object> filter = Map.of("bool", Map.of("filter", filters));
        JsonNode keyword = es.post().uri(INDEX + "/_search").body(Map.of(
                "size", 20, "query", Map.of("bool", Map.of("must", Map.of("match", Map.of("content", query)), "filter", filters))))
                .retrieve().body(JsonNode.class);
        JsonNode vector = es.post().uri(INDEX + "/_search").body(Map.of(
                "size", 20, "knn", Map.of("field", "vector", "query_vector", embeddings.embed(query),
                        "k", 20, "num_candidates", 100, "filter", filter)))
                .retrieve().body(JsonNode.class);
        Map<String, Double> scores = new HashMap<>();
        Map<String, JsonNode> hits = new HashMap<>();
        addRanked(keyword, scores, hits);
        addRanked(vector, scores, hits);
        List<String> ranked = scores.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        List<Source> result = new ArrayList<>();
        for (String id : ranked) {
            JsonNode source = hits.get(id).path("_source");
            long fileId = source.path("fileId").asLong();
            FileInfo file = files.findByIdAndUserId(fileId, userId);
            if (file == null || file.getFilePath() == null || file.getFilePath().isEmpty()
                    || file.getIndexGeneration() == null
                    || file.getIndexGeneration() != source.path("generation").asLong()
                    || file.getVersion() != source.path("version").asInt()) continue;
            result.add(new Source(result.size() + 1, fileId, file.getFileName(),
                    file.getVersion(), source.path("content").asText()));
            if (result.size() >= limit) break;
        }
        return result;
    }

    private void addRanked(JsonNode response, Map<String, Double> scores, Map<String, JsonNode> hits) {
        if (response == null) return;
        int rank = 1;
        for (JsonNode hit : response.path("hits").path("hits")) {
            String id = hit.path("_id").asText();
            hits.put(id, hit);
            scores.merge(id, 1.0 / (60 + rank++), Double::sum);
        }
    }
}
