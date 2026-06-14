package org.knowledgeaap.knowledge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A local stub embedding service that produces deterministic pseudo-random vectors.
 * TODO: Replace with a Spring AI based implementation that calls nomic-embed-text.
 */
@Service
public class EmbeddingServiceStub implements EmbeddingService {

    private final Logger log = LoggerFactory.getLogger(EmbeddingServiceStub.class);
    private final Random random = new Random(42);

    @Override
    public List<float[]> embed(List<String> texts) {
        log.warn("Using EmbeddingServiceStub. Replace with Spring AI implementation for production.");
        List<float[]> result = new ArrayList<>();
        for (String t : texts) {
            float[] v = new float[768];
            for (int i = 0; i < v.length; i++) {
                v[i] = random.nextFloat() - 0.5f;
            }
            result.add(v);
        }
        return result;
    }
}

