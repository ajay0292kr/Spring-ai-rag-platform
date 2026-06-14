package org.knowledgeaap.knowledge.service;

import org.knowledgeaap.knowledge.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class QdrantService {

    private final Logger log = LoggerFactory.getLogger(QdrantService.class);
    private final WebClient webClient;
    private final AppProperties props;

    public QdrantService(AppProperties props) {
        this.props = props;
        this.webClient = WebClient.builder().baseUrl(props.getQdrantUrl()).build();
    }

    public void ensureCollection(String collectionName, int vectorSize) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vectors", Collections.singletonMap("size", vectorSize));
            body.put("shards", 1);
            webClient.put()
                    .uri(uriBuilder -> uriBuilder.path("/collections/{name}").build(collectionName))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> log.warn("Could not create collection (it may already exist): {}", e.getMessage()))
                    .onErrorReturn("")
                    .block();
        }
        catch (Exception e) {
            log.warn("ensureCollection exception: {}", e.getMessage());
        }
    }

    public void upsertVectors(String collectionName, List<String> ids, List<float[]> vectors, List<Map<String, Object>> payloads) {
        if (ids.size() != vectors.size()) throw new IllegalArgumentException("ids and vectors size mismatch");

        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Map<String, Object> point = new HashMap<>();
            point.put("id", ids.get(i));
            point.put("vector", vectors.get(i));
            if (payloads != null && payloads.size() > i) point.put("payload", payloads.get(i));
            points.add(point);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("points", points);

        webClient.put()
                .uri(uriBuilder -> uriBuilder.path("/collections/{name}/points?wait=true").build(collectionName))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("qdrant upsert failed: {}", e.getMessage()))
                .onErrorReturn("")
                .block();
    }

    public List<Map<String, Object>> search(String collectionName, float[] vector, int limit) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vector", vector);
            body.put("limit", limit);
            body.put("with_payload", true);

            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/collections/{name}/points/search").build(collectionName))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> log.error("qdrant search failed: {}", e.getMessage()))
                    .onErrorReturn("{}")
                    .block();

            // Parse response to extract results
            List<Map<String, Object>> results = new ArrayList<>();
            // Simple JSON parsing to extract results
            if (response.contains("\"result\"")) {
                int resultIdx = response.indexOf("\"result\"");
                String resultsStr = response.substring(resultIdx);
                // Extract array of scoring results
                if (resultsStr.contains("[")) {
                    results = parseQdrantSearchResults(resultsStr);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Search exception: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> parseQdrantSearchResults(String json) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            // Basic extraction of payload from Qdrant response
            // In production, use a proper JSON parser like Jackson
            int arrayStart = json.indexOf("[");
            int arrayEnd = json.lastIndexOf("]");
            if (arrayStart > 0 && arrayEnd > arrayStart) {
                String arrayStr = json.substring(arrayStart, arrayEnd + 1);
                // Extract each item from array
                int itemStart = 0;
                while ((itemStart = arrayStr.indexOf("{", itemStart)) > 0) {
                    int itemEnd = arrayStr.indexOf("}", itemStart);
                    if (itemEnd > itemStart) {
                        String item = arrayStr.substring(itemStart, itemEnd + 1);
                        Map<String, Object> result = new HashMap<>();
                        result.put("raw", item);
                        results.add(result);
                        itemStart = itemEnd;
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing Qdrant results: {}", e.getMessage());
        }
        return results;
    }

}

