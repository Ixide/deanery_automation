package kz.yourname.deansoffice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class GeminiService {

    private static final Logger LOGGER = Logger.getLogger(GeminiService.class.getName());

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String generateContent(String promptText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        String requestBodyJsonString = String.format("""
            {
              "contents": [{
                "parts":[{
                  "text": "%s"
                }]
              }]
            }
            """, escapeJson(promptText));

        HttpEntity<String> entity = new HttpEntity<>(requestBodyJsonString, headers);
        String fullApiUrl = apiUrl + "?key=" + apiKey;

        try {
            String response = restTemplate.postForObject(fullApiUrl, entity, String.class);
            return parseGeminiResponse(response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling Gemini API: " + e.getMessage(), e);
            return "Error: Could not generate content from AI. Details: " + e.getMessage();
        }
    }

    private String parseGeminiResponse(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode textNode = rootNode.at("/candidates/0/content/parts/0/text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                JsonNode errorNode = rootNode.at("/error/message");
                if (!errorNode.isMissingNode()) {
                    String errorMessage = "Error from AI: " + errorNode.asText();
                    LOGGER.severe(errorMessage + " Full response: " + jsonResponse);
                    return errorMessage;
                }
                LOGGER.warning("Could not find text in Gemini response: " + jsonResponse);
                return "Error: AI response format not recognized or content is empty.";
            }
            return textNode.asText();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error parsing Gemini JSON response: " + jsonResponse, e);
            return "Error: Could not parse AI response.";
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}