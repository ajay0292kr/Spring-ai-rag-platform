package org.knowledgeaap.knowledge.service;

import org.knowledgeaap.knowledge.config.AppProperties;
import org.knowledgeaap.knowledge.model.Chunk;
import org.knowledgeaap.knowledge.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RAGService {

    private final Logger log = LoggerFactory.getLogger(RAGService.class);
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final OllamaService ollamaService;
    private final ChunkRepository chunkRepository;
    private final AppProperties props;

    public RAGService(EmbeddingService embeddingService,
                      QdrantService qdrantService,
                      OllamaService ollamaService,
                      ChunkRepository chunkRepository,
                      AppProperties props) {
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.ollamaService = ollamaService;
        this.chunkRepository = chunkRepository;
        this.props = props;
    }

    public Mono<String> queryKnowledge(String query, int topK) {
        return Mono.fromCallable(() -> {
            try {
                // Generate embedding for query (run in blocking scheduler)
                List<float[]> queryVectors = embeddingService.embed(List.of(query));
                if (queryVectors.isEmpty()) {
                    return "Error: Could not generate query embedding.";
                }

                float[] queryVector = queryVectors.get(0);

                // Retrieve chunk texts from database and build a cleaned, human-friendly context
                List<Chunk> allChunks = chunkRepository.findAll();
                StringBuilder context = new StringBuilder();

                if (allChunks.isEmpty()) {
                    context.append("No relevant documents found in knowledge base.");
                } else {
                    // Score chunks by cosine similarity against deterministic vectors
                    List<ScoredChunk> scoredChunks = new ArrayList<>();
                    for (Chunk chunk : allChunks) {
                        float score = cosineSimilarity(queryVector, generateDeterministicVector(chunk.getText()));
                        scoredChunks.add(new ScoredChunk(chunk, score));
                    }
                    scoredChunks.sort((a, b) -> Float.compare(b.score, a.score));

                    // Build cleaned, human-friendly excerpts from top results
                    context.append("Relevant context from knowledge base:\n\n");
                    int added = 0;
                    for (ScoredChunk sc : scoredChunks) {
                        if (added >= topK) break;
                        String raw = sc.chunk.getText() == null ? "" : sc.chunk.getText();
                        String clean = cleanText(raw);

                        // Try to pick a sentence that contains query terms
                        String excerpt = pickBestSentence(clean, query);
                        if (excerpt.isEmpty()) {
                            // fallback to a trimmed excerpt
                            excerpt = tidyExcerpt(clean, 320);
                        }

                        // Include light metadata (document filename and chunk index) if available
                        String source = "";
                        try {
                            if (sc.chunk.getDocument() != null && sc.chunk.getDocument().getFilename() != null) {
                                source = " (" + sc.chunk.getDocument().getFilename() + "#" + sc.chunk.getChunkIndex() + ")";
                            }
                        } catch (Exception ignored) {}

                        context.append("- ").append(excerpt).append(source).append("\n\n");
                        added++;
                    }
                    if (added == 0) {
                        context.append("No clean excerpts could be extracted from the top chunks.");
                    }
                }

                // Build prompt
                String prompt = buildPrompt(query, context.toString());

                // Generate response using Ollama
                String response = ollamaService.generateResponse(prompt, "llama2");

                return response;
            } catch (Exception e) {
                log.error("RAG query error: {}", e.getMessage(), e);
                return "Error processing your query: " + e.getMessage();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String buildPrompt(String query, String context) {
        return "You are a helpful assistant. Use the provided context to answer the user's question accurately and concisely.\n\n" +
                "Context:\n" + context + "\n" +
                "Question: " + query + "\n" +
                "Answer: ";
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0f;
        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0f;
        return dotProduct / (float) Math.sqrt(normA * normB);
    }

    private float[] generateDeterministicVector(String text) {
        // Generate a deterministic vector based on text hash
        Random rand = new Random(text.hashCode());
        float[] vector = new float[768];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = rand.nextFloat() - 0.5f;
        }
        return vector;
    }

    // Clean control characters and collapse whitespace
    private String cleanText(String raw) {
        if (raw == null) return "";
        // Replace non-printable/control characters with space
        String s = raw.replaceAll("\\p{Cntrl}", " ");
        // Collapse multiple whitespace/newlines into single space
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    // Pick best sentence containing any query term, fallback empty
    private String pickBestSentence(String text, String query) {
        if (text == null || text.isEmpty()) return "";
        String[] sentences = text.split("(?<=[\\.!?])\\s+");
        String q = query == null ? "" : query.toLowerCase();
        String[] terms = q.split("\\s+");
        for (String sentence : sentences) {
            String lower = sentence.toLowerCase();
            for (String t : terms) {
                if (t.length() > 2 && lower.contains(t)) {
                    return tidyExcerpt(sentence.trim(), 320);
                }
            }
        }
        return "";
    }

    private String tidyExcerpt(String text, int maxLen) {
        if (text == null) return "";
        String s = text.trim();
        if (s.length() <= maxLen) return s;
        // try to cut at sentence boundary
        int idx = s.lastIndexOf('.', maxLen);
        if (idx > 50) return s.substring(0, idx + 1);
        return s.substring(0, maxLen).trim() + "...";
    }

    private static class ScoredChunk {
        Chunk chunk;
        float score;

        ScoredChunk(Chunk chunk, float score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}

