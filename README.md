# Knowledge Base Assistant

A local RAG-style knowledge base assistant built with Spring Boot, PostgreSQL, Qdrant, and Ollama. It lets you upload documents, indexes extracted text into chunks, and answers questions using retrieved document context plus a locally running Ollama model.

## What Is Included

- Spring Boot backend using WebFlux and Spring Data JPA
- Local HTML UI for upload, document management, and chat/search
- PostgreSQL for document and chunk metadata
- Qdrant for vector collection creation and vector upsert
- Ollama for local LLM responses
- Apache Tika-based document text extraction
- Configurable chunk size, chunk overlap, upload directory, and Qdrant collection

## Current Behavior

Uploaded files are saved under `uploads/`, metadata is stored in PostgreSQL, text is extracted and chunked, embeddings are generated, and vectors are upserted into Qdrant.

The current embedding implementation is `EmbeddingServiceStub`, which creates deterministic pseudo-random vectors. This is useful for local wiring and testing, but it is not production semantic search. Replace it with a real Ollama/Spring AI embedding implementation, for example using `nomic-embed-text`, before relying on search quality.

The query flow retrieves indexed chunks from PostgreSQL, builds a prompt with relevant context, and sends it to Ollama. The Ollama service now picks a valid installed model from `/api/tags`, preferring the requested model if present and otherwise using `mistral:latest` when available.

## Requirements

- Java 25
- Maven 3.9+
- Docker Desktop
- Ollama model downloaded in the Docker Ollama container, for example `mistral`

## Ports

| Service | Port | URL |
| --- | ---: | --- |
| UI static server | 8000 | `http://localhost:8000/knowledge-ui.html` |
| Spring Boot backend | 8082 | `http://localhost:8082` |
| PostgreSQL | 5432 | `localhost:5432` |
| Qdrant | 6333 | `http://localhost:6333` |
| Ollama | 11434 | `http://localhost:11434` |

Port `8080` may already be used by other local services, so the backend is commonly run on `8082`. The included `knowledge-ui.html` is configured to call `http://localhost:8082/api`.

## Run Locally

Start the infrastructure services:

```bash
docker compose -f compose.yaml up -d
```

Check Ollama models:

```bash
curl http://localhost:11434/api/tags
```

If no model is installed, pull one:

```bash
docker exec knowledge-ollama-1 ollama pull mistral
```

Build the backend:

```bash
mvn -DskipTests package
```

Run the backend on port `8082`:

```bash
java -jar target/knowledge-0.0.1-SNAPSHOT.jar --server.port=8082
```

Serve the UI over HTTP:

```bash
python3 -m http.server 8000
```

Open:

```text
http://localhost:8000/knowledge-ui.html
```

Do not open the UI with `file:///.../knowledge-ui.html`. Use the local HTTP URL above so browser CORS and upload behavior are consistent.

## API Examples

Upload a document:

```bash
curl -F "file=@/path/to/document.pdf" http://localhost:8082/api/documents
```

List indexed documents:

```bash
curl http://localhost:8082/api/documents
```

Ask a question:

```bash
curl -X POST http://localhost:8082/api/documents/query \
  -H "Content-Type: application/json" \
  -d '{"query":"What is this document about?","topK":5}'
```

Reindex a document:

```bash
curl -X POST http://localhost:8082/api/documents/1/reindex
```

Delete a document:

```bash
curl -X DELETE http://localhost:8082/api/documents/1
```

## Configuration

Main configuration lives in `src/main/resources/application.properties`.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/knowledge
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update

knowledge.chunk-size=1000
knowledge.chunk-overlap=200
knowledge.qdrant-url=http://localhost:6333
knowledge.qdrant-collection=knowledge_collection
knowledge.upload-dir=./uploads
```

The UI API base is defined in `knowledge-ui.html`:

```javascript
const API_BASE = 'http://localhost:8082/api';
```

## Important Implementation Notes

- Uploads are handled reactively. The file transfer completes first, then blocking extraction/indexing work runs on Reactor `boundedElastic`.
- CORS allows the local UI origin `http://localhost:8000`.
- Qdrant collection creation and vector upsert happen through the backend service.
- Chunk metadata is stored in PostgreSQL.
- Ollama fallback text is returned only when Ollama is unavailable or generation fails.

## Troubleshooting

If upload fails in the browser, make sure you opened the UI from:

```text
http://localhost:8000/knowledge-ui.html
```

If the backend is not reachable, verify it is listening:

```bash
lsof -nP -iTCP:8082 -sTCP:LISTEN
```

If Ollama answers are not generated, verify the model list:

```bash
curl http://localhost:11434/api/tags
```

If Qdrant is not reachable:

```bash
curl http://localhost:6333/collections
```

If PostgreSQL, Qdrant, or Ollama are not running:

```bash
docker compose -f compose.yaml up -d
```

## Production TODOs

- Replace `EmbeddingServiceStub` with real embedding generation.
- Persist updated Qdrant point IDs back onto chunks after upload/reindex.
- Add authentication and authorization.
- Add upload size limits and stronger file validation.
- Add integration tests for upload, indexing, querying, and Ollama fallback behavior.
- Replace the standalone HTML UI with a maintained frontend build if this becomes a deployable application.
