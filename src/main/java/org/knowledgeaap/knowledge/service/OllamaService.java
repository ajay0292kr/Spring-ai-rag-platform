package org.knowledgeaap.knowledge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OllamaService {

    private final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private final WebClient webClient;
    private volatile boolean ollamaAvailable = true;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern OLLAMA_MODEL_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    public OllamaService() {
        this.webClient = WebClient.builder().baseUrl("http://localhost:11434").build();
    }

    public String generateResponse(String prompt, String model) {
        // If Ollama previously failed, probe again (models may become available)
        if (!ollamaAvailable) {
            try {
                String probe = webClient.get().uri("/api/tags").retrieve().bodyToMono(String.class).onErrorReturn("").block();
                if (probe != null && probe.contains("\"models\"") && !probe.contains("\"models\": []")) {
                    ollamaAvailable = true;
                }
            } catch (Exception ignored) {
            }
        }

        try {
            String modelToUse = pickAvailableModelOrDefault(model);

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelToUse);
            body.put("prompt", prompt);
            body.put("stream", false);

            String resp = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> log.warn("Ollama generate failed: {}", e.getMessage()))
                    .onErrorReturn("")
                    .block();

            if (resp == null || resp.isEmpty()) {
                ollamaAvailable = false;
                return generateFallbackResponse(prompt);
            }

            // parse JSON response robustly
            try {
                JsonNode root = objectMapper.readTree(resp);
                if (root.has("response")) {
                    return root.get("response").asText();
                }
                // some Ollama builds return 'text' or other fields
                if (root.has("text")) return root.get("text").asText();
            } catch (Exception ex) {
                log.warn("Failed to parse Ollama response JSON: {}", ex.getMessage());
            }

            ollamaAvailable = false;
            return generateFallbackResponse(prompt);
        } catch (Exception e) {
            log.warn("Ollama exception: {}", e.getMessage());
            ollamaAvailable = false;
            return generateFallbackResponse(prompt);
        }
    }

    private String generateFallbackResponse(String prompt) {
        // Generate a reasonable response from the prompt and context
        // Extract the question from prompt
        if (prompt.contains("Question:")) {
            String[] parts = prompt.split("Question:");
            if (parts.length > 1) {
                String question = parts[1].replace("Answer:", "").trim();

                // Generate intelligent fallback response based on context
                if (prompt.contains("Context:") && !prompt.contains("No relevant documents")) {
                    String[] contextParts = prompt.split("Context:");
                    if (contextParts.length > 1) {
                        String context = contextParts[1].split("Question:")[0].trim();
                        // Generate a summary-based response
                        return "Based on the documents in your knowledge base:\n\n" +
                               generateSummaryFromContext(context) +
                               "\n\n(Note: Ollama is not available or doesn't have models. Using context-based response. To enable full AI responses, run: docker exec knowledge-ollama-1 ollama pull mistral)";
                    }
                }
            }
        }

        return "I found relevant information in your knowledge base to answer your question. " +
               "(Note: Ollama is not currently available or doesn't have downloaded models. " +
               "To enable AI-powered responses, download a model using: docker exec knowledge-ollama-1 ollama pull mistral)";
    }

    private String generateSummaryFromContext(String context) {
        // Extract key information from context
        String[] lines = context.split("\n");
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (!line.trim().isEmpty() && !line.startsWith("-")) {
                continue;
            }
            if (line.startsWith("- ") && count < 3) {
                summary.append(line).append("\n");
                count++;
            }
        }
        return summary.length() > 0 ? summary.toString() : "Information from your knowledge base documents addresses your question.";
    }

    private String extractResponseText(String json) {
        try {
            // Find "response": field and extract value
            int startIdx = json.indexOf("\"response\":\"");
            if (startIdx > 0) {
                int valueStart = startIdx + 12;
                int valueEnd = json.indexOf("\"", valueStart);
                if (valueEnd > valueStart) {
                    String response = json.substring(valueStart, valueEnd);
                    // Unescape JSON string
                    response = response.replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                    return response;
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting response text: {}", e.getMessage());
        }
        return json; // Fallback to full response if parsing fails
    }

    private String pickAvailableModelOrDefault(String preferred) {
        try {
            String resp = webClient.get().uri("/api/tags").retrieve().bodyToMono(String.class).onErrorReturn("").block();
            if (resp != null) {
                Matcher matcher = OLLAMA_MODEL_NAME.matcher(resp);
                String firstModel = null;
                String mistralModel = null;
                while (matcher.find()) {
                    String name = matcher.group(1);
                    if (firstModel == null) {
                        firstModel = name;
                    }
                    if (name.equals(preferred) || name.equals(preferred + ":latest")) {
                        return name;
                    }
                    if (name.startsWith("mistral:")) {
                        mistralModel = name;
                    }
                }
                if (mistralModel != null) return mistralModel;
                if (firstModel != null) return firstModel;
            }
        } catch (Exception e) {
            log.debug("Could not list ollama models: {}", e.getMessage());
        }
        return preferred;
    }
}
