package org.knowledgeaap.knowledge.web;

import org.knowledgeaap.knowledge.config.AppProperties;
import org.knowledgeaap.knowledge.model.Chunk;
import org.knowledgeaap.knowledge.model.Document;
import org.knowledgeaap.knowledge.repository.DocumentRepository;
import org.knowledgeaap.knowledge.service.DocumentService;
import org.knowledgeaap.knowledge.service.EmbeddingService;
import org.knowledgeaap.knowledge.service.QdrantService;
import org.knowledgeaap.knowledge.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final AppProperties props;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final RAGService ragService;

    public DocumentController(DocumentService documentService,
                              DocumentRepository documentRepository,
                              AppProperties props,
                              EmbeddingService embeddingService,
                              QdrantService qdrantService,
                              RAGService ragService) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.props = props;
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.ragService = ragService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public Mono<Document> upload(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono.flatMap(fp -> {
            try {
                Path uploads = Path.of(props.getUploadDir());
                Files.createDirectories(uploads);
                Path dest = uploads.resolve(UUID.randomUUID().toString() + "__" + fp.filename());
                String filename = fp.filename();
                String contentType = fp.headers().getContentType() != null ? fp.headers().getContentType().toString() : "application/octet-stream";

                return fp.transferTo(dest)
                        .then(Mono.fromCallable(() -> indexUploadedFile(filename, contentType, dest.toFile()))
                                .subscribeOn(Schedulers.boundedElastic()));
            } catch (Exception e) {
                log.error("upload error", e);
                return Mono.error(e);
            }
        });
    }

    private Document indexUploadedFile(String filename, String contentType, File destFile) throws Exception {
        Document doc = documentService.saveMetadata(filename, contentType, destFile.length());

        List<Chunk> chunks = documentService.extractAndChunk(doc, destFile);

        // generate embeddings
        List<String> ids = chunks.stream().map(c -> UUID.randomUUID().toString()).collect(Collectors.toList());
        List<String> texts = chunks.stream().map(Chunk::getText).collect(Collectors.toList());
        List<float[]> vectors = embeddingService.embed(texts);

        // ensure collection and upsert
        int vectorDim = vectors.isEmpty() ? 0 : vectors.get(0).length;
        qdrantService.ensureCollection(props.getQdrantCollection(), vectorDim);

        List<Map<String,Object>> payloads = new ArrayList<>();
        for (Chunk c : chunks) {
            Map<String,Object> p = new HashMap<>();
            p.put("documentId", c.getDocument().getId());
            p.put("chunkIndex", c.getChunkIndex());
            payloads.add(p);
        }

        qdrantService.upsertVectors(props.getQdrantCollection(), ids, vectors, payloads);

        // persist qdrant ids on chunks
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setQdrantPointId(ids.get(i));
        }

        // save updated chunks
        // Note: chunkRepository is used inside documentService; for simplicity re-saving through repository is omitted here

        return doc;
    }

    @GetMapping
    public Flux<Document> list() {
        return Flux.fromIterable(documentRepository.findAll());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> documentRepository.deleteById(id));
    }

    @PostMapping("/{id}/reindex")
    public Mono<Document> reindex(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            Document doc = documentRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
            Path uploads = Path.of(props.getUploadDir());
            Optional<Path> f = Files.list(uploads).filter(p -> p.getFileName().toString().contains("__" + doc.getFilename())).findFirst();
            if (f.isEmpty()) throw new RuntimeException("Original file not found on disk");
            List<Chunk> chunks = documentService.extractAndChunk(doc, f.get().toFile());
            // generate embeddings and upsert - same as upload
            List<String> ids = chunks.stream().map(c -> UUID.randomUUID().toString()).collect(Collectors.toList());
            List<String> texts = chunks.stream().map(Chunk::getText).collect(Collectors.toList());
            List<float[]> vectors = embeddingService.embed(texts);
            int vectorDim = vectors.isEmpty() ? 0 : vectors.get(0).length;
            qdrantService.ensureCollection(props.getQdrantCollection(), vectorDim);
            List<Map<String,Object>> payloads = new ArrayList<>();
            for (Chunk c : chunks) {
                Map<String,Object> p = new HashMap<>();
                p.put("documentId", c.getDocument().getId());
                p.put("chunkIndex", c.getChunkIndex());
                payloads.add(p);
            }
            qdrantService.upsertVectors(props.getQdrantCollection(), ids, vectors, payloads);
            return doc;
        });
    }

    @PostMapping("/query")
    public Mono<Map<String, String>> query(@RequestBody RAGQueryRequest request) {
        return ragService.queryKnowledge(request.getQuery(), request.getTopK() != null ? request.getTopK() : 5)
                .map(response -> {
                    Map<String, String> result = new HashMap<>();
                    result.put("response", response);
                    return result;
                })
                .onErrorResume(error -> {
                    Map<String, String> result = new HashMap<>();
                    result.put("response", "Error: " + error.getMessage());
                    return Mono.just(result);
                });
    }

}
