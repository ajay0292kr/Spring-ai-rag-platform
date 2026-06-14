package org.knowledgeaap.knowledge.service;

import java.util.List;

/**
 * Abstract embedding service. Implementation should use Spring AI to generate embeddings.
 */
public interface EmbeddingService {
    /**
     * Generate embedding vectors for the provided texts. Each embedding is a float array.
     */
    List<float[]> embed(List<String> texts);
}

