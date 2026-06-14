package org.knowledgeaap.knowledge.service;

import org.apache.tika.Tika;
import org.knowledgeaap.knowledge.config.AppProperties;
import org.knowledgeaap.knowledge.model.Chunk;
import org.knowledgeaap.knowledge.model.Document;
import org.knowledgeaap.knowledge.repository.ChunkRepository;
import org.knowledgeaap.knowledge.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final AppProperties props;
    private final Tika tika = new Tika();

    public DocumentService(DocumentRepository documentRepository,
                           ChunkRepository chunkRepository,
                           AppProperties props) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.props = props;
    }

    public Document saveMetadata(String filename, String contentType, long size) {
        Document doc = new Document();
        doc.setFilename(filename);
        doc.setContentType(contentType);
        doc.setSize(size);
        doc.setUploadedAt(Instant.now());
        doc.setStatus("UPLOADED");
        doc.setQdrantCollection(props.getQdrantCollection());
        return documentRepository.save(doc);
    }

    public List<Chunk> extractAndChunk(Document document, File file) throws Exception {
        String text = tika.parseToString(file);
        int chunkSize = props.getChunkSize();
        int overlap = props.getChunkOverlap();

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("knowledge.chunk-size must be greater than 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("knowledge.chunk-overlap must be greater than or equal to 0 and less than knowledge.chunk-size");
        }

        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int idx = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunkText = text.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                Chunk c = new Chunk();
                c.setDocument(document);
                c.setChunkIndex(idx++);
                c.setText(chunkText);
                chunks.add(c);
            }
            if (end == text.length()) break;
            start = Math.max(0, end - overlap);
        }

        List<Chunk> saved = chunkRepository.saveAll(chunks);
        document.setStatus("INDEXED");
        documentRepository.save(document);
        return saved;
    }

}
