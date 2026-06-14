package org.knowledgeaap.knowledge.repository;

import org.knowledgeaap.knowledge.model.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, Long> {
    List<Chunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);
}

