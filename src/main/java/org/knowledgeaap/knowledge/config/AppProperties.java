
package org.knowledgeaap.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "knowledge")
public class AppProperties {
    private int chunkSize = 1000;
    private int chunkOverlap = 200;
    private String qdrantUrl = "http://localhost:6333";
    private String qdrantCollection = "knowledge_collection";
    private String uploadDir = "./uploads";

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public String getQdrantUrl() { return qdrantUrl; }
    public void setQdrantUrl(String qdrantUrl) { this.qdrantUrl = qdrantUrl; }

    public String getQdrantCollection() { return qdrantCollection; }
    public void setQdrantCollection(String qdrantCollection) { this.qdrantCollection = qdrantCollection; }

    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
}

