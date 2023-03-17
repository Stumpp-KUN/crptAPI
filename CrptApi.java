package com.example.ff;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrptApi {

    private static final String BASE_URL = "https://<server-name>[:server-port]/api/v2/{extension}";
    private static final String CREATE_DOCUMENT_PATH = "/documents/create";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private final Logger logger= LogManager.getRootLogger();

    private CloseableHttpClient httpClient = HttpClients.createDefault();
    private ObjectMapper objectMapper = new ObjectMapper();
    private Bucket bucket;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        Bandwidth limit = Bandwidth.simple(requestLimit, Duration.ofMillis(timeUnit.toMillis(1)));
        bucket = Bucket4j.builder().addLimit(limit).build();
    }

    public void createDocument(Object document, String signature, String sessionKey) throws IOException {
        if (sessionKey == null || sessionKey.isEmpty()) {
            throw new IllegalArgumentException("Session key is needed");
        }
        DocumentRequest request = new DocumentRequest(document, signature);
        String jsonRequest = objectMapper.writeValueAsString(request);

        HttpPost postRequest = new HttpPost(BASE_URL + CREATE_DOCUMENT_PATH);
        postRequest.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        postRequest.setHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + sessionKey);
        postRequest.setEntity(new StringEntity(jsonRequest));

        bucket.asScheduler().consume(1);
        try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
            HttpEntity entity = response.getEntity();
            DocumentResponse documentResponse = objectMapper.readValue(entity.getContent(), DocumentResponse.class);
            logger.info("Document uuid:" + documentResponse.getUuid());
            logger.info("Document status:" + documentResponse.getStatus());
            entity.getContent().close();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Getter
    @Setter
    private static class DocumentRequest {
        private Object document;
        private String signature;
        public DocumentRequest(Object document, String signature) {
            this.document = document;
            this.signature = signature;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class DocumentResponse {
        private String uuid;
        private String status;
    }

}
