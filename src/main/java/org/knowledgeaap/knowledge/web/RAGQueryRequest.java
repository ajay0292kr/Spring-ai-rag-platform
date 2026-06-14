package org.knowledgeaap.knowledge.web;

public class RAGQueryRequest {
    private String query;
    private Integer topK;

    public RAGQueryRequest() {}

    public RAGQueryRequest(String query, Integer topK) {
        this.query = query;
        this.topK = topK;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}

