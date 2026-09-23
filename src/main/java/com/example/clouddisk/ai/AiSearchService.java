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
import ai.djl.engine.Engine;
import jakarta.annotation.PostConstruct;

import java.util.*;

@Service
public class AiSearchService {
    private static final String INDEX = "/cloud_disk_chunks";
    private final RestClient es;
    private final EmbeddingModel embeddings;
    private final FileMapper files;
    private volatile boolean indexReady;

    public record Source(int number, Long fileId, String fileName, int version, String snippet) {}
    public record Document(Source source, String content) {}

    public AiSearchService(@Value("${ai.elasticsearch.url}") String url,
                           EmbeddingModel embeddings, FileMapper files) {
        this.es = RestClient.builder().baseUrl(url).build();
        this.embeddings = embeddings;
        this.files = files;
    }

    @PostConstruct
    void initializeLocalModel() {
        Engine.getEngine("PyTorch");
        embed("云盘文档");
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
            document.put("vector", embed(content));
            es.put().uri(INDEX + "/_doc/{id}", file.getId() + ":" + generation + ":" + i)
                    .body(document).retrieve().toBodilessEntity();
        }
        es.post().uri(INDEX + "/_refresh").retrieve().toBodilessEntity();
    }

    public List<Source> search(Long userId, String query, List<Long> fileIds, int limit) {
        if (query == null || query.isBlank()) return List.of();
        ensureIndex();
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("ownerId", userId)));
        if (fileIds != null && !fileIds.isEmpty()) filters.add(Map.of("terms", Map.of("fileId", fileIds)));
        Map<String, Object> filter = Map.of("bool", Map.of("filter", filters));
        boolean exact = query.trim().matches("[\\p{IsHan}]{1,4}|[A-Za-z]+");
        if (exact) {
            JsonNode matches = es.post().uri(INDEX + "/_search").body(Map.of(
                    "size", 100, "query", Map.of("bool", Map.of("must",
                            Map.of("match_phrase", Map.of("content", query.trim())), "filter", filters)),
                    "collapse", Map.of("field", "fileId")))
                    .retrieve().body(JsonNode.class);
            List<JsonNode> hits = new ArrayList<>();
            for (JsonNode hit : matches.path("hits").path("hits"))
                if (hit.path("_source").path("content").asText().toLowerCase(Locale.ROOT)
                        .contains(query.trim().toLowerCase(Locale.ROOT))) hits.add(hit);
            return validSources(userId, hits, limit).stream()
                    .map(source -> new Source(source.number(), source.fileId(), source.fileName(),
                            source.version(), excerpt(source.snippet(), query.trim()))).toList();
        }
        JsonNode keyword = es.post().uri(INDEX + "/_search").body(Map.of(
                "size", 50, "query", Map.of("bool", Map.of("must", Map.of("match", Map.of("content", Map.of("query", query, "minimum_should_match", "70%"))), "filter", filters))))
                .retrieve().body(JsonNode.class);
        JsonNode vector = es.post().uri(INDEX + "/_search").body(Map.of(
                "size", 50, "knn", Map.of("field", "vector", "query_vector", embed(query),
                        "k", 50, "num_candidates", 200, "similarity", 0.50, "filter", filter)))
                .retrieve().body(JsonNode.class);
        Map<String, Double> scores = new HashMap<>();
        Map<String, JsonNode> hits = new HashMap<>();
        addRanked(keyword, scores, hits);
        addRanked(vector, scores, hits);
        List<String> ranked = scores.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        List<Source> candidates = validSources(userId, ranked.stream().map(hits::get).toList(), 50);
        List<Source> diverse = new ArrayList<>();
        Map<Long, Integer> perFile = new HashMap<>();
        int perFileLimit = fileIds != null && fileIds.size() == 1 ? limit : 2;
        for (Source candidate : candidates) {
            if (perFile.getOrDefault(candidate.fileId(), 0) >= perFileLimit) continue;
            perFile.merge(candidate.fileId(), 1, Integer::sum);
            diverse.add(new Source(diverse.size() + 1, candidate.fileId(), candidate.fileName(),
                    candidate.version(), candidate.snippet()));
            if (diverse.size() >= limit) break;
        }
        return diverse;
    }

    public boolean isOverviewQuestion(String query) {
        return List.of("讲了啥", "讲了什么", "说了什么", "总结", "概括", "摘要", "主要内容",
                        "内容是什么", "概述", "有什么", "有哪些", "什么要求")
                .stream().anyMatch(query::contains);
    }

    public List<Document> overview(Long userId, String question, List<Long> fileIds) {
        ensureIndex();
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("ownerId", userId)));
        if (fileIds != null && !fileIds.isEmpty()) filters.add(Map.of("terms", Map.of("fileId", fileIds)));
        JsonNode response = es.post().uri(INDEX + "/_search").body(Map.of(
                "size", 1000, "query", Map.of("bool", Map.of("filter", filters))))
                .retrieve().body(JsonNode.class);
        if (response.path("hits").path("total").path("value").asLong() > 1000)
            throw new IllegalArgumentException("相关文档过多，请缩小文件范围后重试");
        String topic = question.replaceAll("(?i)(有什么|有哪些|什么要求|总结|概括|摘要|概述|讲了啥|讲了什么|说了什么|主要内容|内容是什么|这几份|这些|两份|几份|一份|所有|全部|文档|文件|要求|任务|题目|关于|请|帮我|一下|我的|里面|其中|的|里|中|？|\\?)", "").trim();
        Map<Long, List<JsonNode>> groups = new LinkedHashMap<>();
        for (JsonNode hit : response.path("hits").path("hits"))
            groups.computeIfAbsent(hit.path("_source").path("fileId").asLong(), unused -> new ArrayList<>()).add(hit);
        List<Document> documents = new ArrayList<>();
        int total = 0;
        for (Map.Entry<Long, List<JsonNode>> group : groups.entrySet()) {
            FileInfo file = files.findByIdAndUserId(group.getKey(), userId);
            if (file == null || file.getFilePath() == null || file.getFilePath().isEmpty()
                    || file.getIndexGeneration() == null) continue;
            if (!topic.isEmpty() && !file.getFileName().contains(topic)
                    && group.getValue().stream().noneMatch(hit -> hit.path("_source").path("content").asText().contains(topic)))
                continue;
            StringBuilder content = new StringBuilder();
            group.getValue().sort(Comparator.comparingInt(hit -> chunkNumber(hit.path("_id").asText())));
            for (JsonNode hit : group.getValue()) {
                JsonNode data = hit.path("_source");
                if (file.getIndexGeneration() != data.path("generation").asLong()
                        || file.getVersion() != data.path("version").asInt()) continue;
                content.append(data.path("content").asText()).append('\n');
            }
            if (content.isEmpty()) continue;
            total += content.length();
            if (total > 60_000) throw new IllegalArgumentException("相关文档超过 6 万字，请缩小文件范围后重试");
            int number = documents.size() + 1;
            documents.add(new Document(new Source(number, file.getId(), file.getFileName(),
                    file.getVersion(), excerpt(content.toString(), topic)), content.toString()));
        }
        return documents;
    }

    private int chunkNumber(String id) {
        try { return Integer.parseInt(id.substring(id.lastIndexOf(':') + 1)); }
        catch (Exception ignored) { return 0; }
    }

    private String excerpt(String content, String query) {
        int at = query.isEmpty() ? 0 : content.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (at < 0) at = 0;
        int from = Math.max(0, at - 70);
        int to = Math.min(content.length(), at + Math.max(query.length(), 1) + 110);
        return (from > 0 ? "…" : "") + content.substring(from, to).trim() + (to < content.length() ? "…" : "");
    }

    private List<Source> validSources(Long userId, List<JsonNode> ranked, int limit) {
        List<Source> result = new ArrayList<>();
        for (JsonNode hit : ranked) {
            JsonNode source = hit.path("_source");
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

    private synchronized float[] embed(String text) {
        return embeddings.embed(text);
    }
}
