package kz.yourname.deansoffice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

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

        // Тело запроса для Gemini API (структура может отличаться, см. документацию Gemini)
        // Пример для Gemini Pro (может потребоваться адаптация)
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
            // Парсинг ответа для извлечения сгенерированного текста
            return parseGeminiResponse(response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling Gemini API: " + e.getMessage(), e);
            return "Error: Could not generate content from AI. " + e.getMessage();
        }
    }

    private String parseGeminiResponse(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            // Путь к тексту может отличаться в зависимости от модели и версии API
            // Примерный путь: response.candidates[0].content.parts[0].text
            JsonNode textNode = rootNode.at("/candidates/0/content/parts/0/text");
            if (textNode.isMissingNode()) {
                // Проверяем наличие ошибок в ответе API
                JsonNode errorNode = rootNode.at("/error/message");
                if (!errorNode.isMissingNode()) {
                    LOGGER.severe("Gemini API Error: " + errorNode.asText());
                    return "Error from AI: " + errorNode.asText();
                }
                LOGGER.warning("Could not find text in Gemini response: " + jsonResponse);
                return "Error: AI response format not recognized or content is empty.";
            }
            return textNode.asText();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error parsing Gemini JSON response: " + e.getMessage(), e);
            return "Error: Could not parse AI response.";
        }
    }

    // Вспомогательный метод для экранирования спецсимволов в JSON строке
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