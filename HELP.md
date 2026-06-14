# Getting Started

### Reference Documentation

For further reference, please consider the following sections:

* [Official Maven documentation](https://maven.apache.org)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.0/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.0/gradle-plugin/packaging-oci-image.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.1.0/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Ollama](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
* [Spring Reactive Web](https://docs.spring.io/spring-boot/4.1.0/reference/web/reactive.html)

### Guides

The following guides illustrate how to use some features concretely:

* [Building a Reactive RESTful Web Service](https://spring.io/guides/gs/reactive-rest-service/)

### Additional Links

These additional references should also help you:

* [Maven Central – dependency repository](https://search.maven.org)

### Docker Compose support

This project contains a Docker Compose file named `compose.yaml`.
In this file, the following services have been defined:

* ollama: [`ollama/ollama:latest`](https://hub.docker.com/r/ollama/ollama)

Please review the tags of the used images and set them to the same as you're running in production.

