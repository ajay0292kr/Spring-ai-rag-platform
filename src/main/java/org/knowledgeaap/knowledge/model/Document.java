
package org.knowledgeaap.knowledge.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    private String contentType;

    private Long size;

    private Instant uploadedAt;

    private String status;

    private String qdrantCollection;

    public Document() {}

    public Document(Long id, String filename, String contentType, Long size, Instant uploadedAt, String status, String qdrantCollection) {
        this.id = id;
        this.filename = filename;
        this.contentType = contentType;
        this.size = size;
        this.uploadedAt = uploadedAt;
        this.status = status;
        this.qdrantCollection = qdrantCollection;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getQdrantCollection() { return qdrantCollection; }
    public void setQdrantCollection(String qdrantCollection) { this.qdrantCollection = qdrantCollection; }

}

