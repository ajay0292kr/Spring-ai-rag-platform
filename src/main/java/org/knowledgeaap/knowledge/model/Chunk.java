
package org.knowledgeaap.knowledge.model;

import jakarta.persistence.*;

@Entity
@Table(name = "chunks")
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    private Integer chunkIndex;

    @Column(columnDefinition = "text")
    private String text;

    // Qdrant point id (string) after upsert
    private String qdrantPointId;

    public Chunk() {}

    public Chunk(Long id, Document document, Integer chunkIndex, String text, String qdrantPointId) {
        this.id = id;
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.qdrantPointId = qdrantPointId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getQdrantPointId() { return qdrantPointId; }
    public void setQdrantPointId(String qdrantPointId) { this.qdrantPointId = qdrantPointId; }

}

