package kz.yourname.deansoffice.service;

import kz.yourname.deansoffice.dto.ExamTicketRequest;
import kz.yourname.deansoffice.dto.SyllabusRequest;
import kz.yourname.deansoffice.model.*;
import kz.yourname.deansoffice.repository.ExamTicketRepository;
import kz.yourname.deansoffice.repository.SyllabusRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentGenerationService {

    private final GeminiService geminiService;
    private final SyllabusRepository syllabusRepository;
    private final ExamTicketRepository examTicketRepository;

    public DocumentGenerationService(GeminiService geminiService,
                                     SyllabusRepository syllabusRepository,
                                     ExamTicketRepository examTicketRepository) {
        this.geminiService = geminiService;
        this.syllabusRepository = syllabusRepository;
        this.examTicketRepository = examTicketRepository;
    }

    private String formatTextForHtml(String text) {
        if (text == null || text.isBlank() || text.startsWith("Раздел") || text.startsWith("Ошибка")) {
            return text;
        }
        return text.replace(System.lineSeparator(), "<br/>").replace("\n", "<br/>");
    }

    public Syllabus generateSyllabus(SyllabusRequest request) {
        String prompt = String.format(
                "Создай силлабус для дисциплины \"%s\" для специальности \"%s\". " +
                        "Образовательные цели: %s. " +
                        "Силлабус должен включать: " +
                        "1. Описание курса. " +
                        "2. Темы лекций (не менее 5 тем), соответствующие образовательным целям. " +
                        "3. Темы практических занятий (не менее 5 тем), соответствующие образовательным целям. " +
                        "4. Список рекомендуемой литературы (не менее 3 источников с авторами и годом издания), актуальный для специальности. " +
                        "5. Критерии оценки: опиши подробно возможные варианты системы оценивания (например, балльно-рейтинговая) с указанием весов для каждого вида контроля. " +
                        "Ответ дай в структурированном виде, используя маркеры для каждого раздела и подраздела (например, 'Описание курса:', 'Темы лекций:', и т.д.).",
                request.getDisciplineName(),
                request.getSpecialty(),
                request.getEducationalGoals()
        );

        String generatedTextFromAI = geminiService.generateContent(prompt);
        String cleanGeneratedText = generatedTextFromAI.replace("**", ""); // Убираем Markdown жирность

        Syllabus syllabus = new Syllabus();
        syllabus.setDisciplineInfo(new DisciplineInfo(request.getDisciplineName(), request.getSpecialty(), request.getEducationalGoals()));
        syllabus.setGeneratedRawText(cleanGeneratedText);

        try {
            syllabus.setCourseDescription(formatTextForHtml(extractSection(cleanGeneratedText, "Описание курса:", "Темы лекций:")));
            syllabus.setLectureTopics(extractList(cleanGeneratedText, "Темы лекций:", "Темы практических занятий:"));
            syllabus.setPracticalTopics(extractList(cleanGeneratedText, "Темы практических занятий:", "Список рекомендуемой литературы:"));
            syllabus.setLiteratureList(extractList(cleanGeneratedText, "Список рекомендуемой литературы:", "Критерии оценки:"));

            String criteriaBlockText = extractSection(cleanGeneratedText, "Критерии оценки:", null);
            AssessmentCriteria criteria = new AssessmentCriteria();
            if (criteriaBlockText != null && !(criteriaBlockText.startsWith("Раздел") || criteriaBlockText.startsWith("Ошибка"))) {
                String[] criteriaLines = criteriaBlockText.split("\\R");
                criteria.setGradingSystemType(criteriaLines.length > 0 ? criteriaLines[0] : "Тип системы не указан"); // Пример
                criteria.setCriteriaDetails(Map.of("Пример экзамена", "50%", "Пример заданий", "50%")); // Заглушка
                criteria.setDetailedBreakdown(Arrays.stream(criteriaLines).map(this::formatTextForHtml).collect(Collectors.toList()));
            } else {
                criteria.setGradingSystemType("Нет данных");
                criteria.setDetailedBreakdown(List.of(criteriaBlockText != null ? criteriaBlockText : "Блок критериев не найден."));
            }
            syllabus.setAssessmentCriteria(criteria);

        } catch (Exception e) {
            System.err.println("Ошибка парсинга ответа Gemini для силлабуса: " + e.getMessage());
            if (syllabus.getCourseDescription() == null) syllabus.setCourseDescription(formatTextForHtml("Ошибка парсинга описания курса."));
        }
        return syllabusRepository.save(syllabus);
    }

    public List<ExamTicket> generateExamTickets(ExamTicketRequest request) {
        List<ExamTicket> tickets = new ArrayList<>();
        String basePrompt = String.format(
                "Сгенерируй экзаменационный билет для дисциплины \"%s\". " +
                        "Темы для билетов: %s. " +
                        "Каждый билет должен содержать: " +
                        "1. Два теоретических вопроса (каждый вопрос должен начинаться с нового номера, например, '1. Текст вопроса', '2. Текст вопроса'). " +
                        "2. Одну практическую задачу. " +
                        "Вопросы и задачи должны быть четко сформулированы. " +
                        "Разделы 'Теоретические вопросы:' и 'Практическая задача:' должны быть явно выделены. После практической задачи поставь разделитель '---'.",
                request.getDisciplineName(),
                request.getTopicsToCover()
        );

        for (int i = 1; i <= request.getNumberOfTickets(); i++) {
            String ticketPrompt = basePrompt + String.format("\nЭто билет номер %d.", i);
            String generatedTextFromAI = geminiService.generateContent(ticketPrompt);
            String cleanGeneratedText = generatedTextFromAI.replace("**", "");

            ExamTicket ticket = new ExamTicket();
            ticket.setDisciplineName(request.getDisciplineName());
            ticket.setTicketNumber(i);
            ticket.setGeneratedRawText(cleanGeneratedText);

            try {
                String theoreticalBlockRaw = extractSection(cleanGeneratedText, "Теоретические вопросы:", "Практическая задача:");
                List<String> theoreticalQuestions = new ArrayList<>();
                if (theoreticalBlockRaw != null && !(theoreticalBlockRaw.startsWith("Раздел") || theoreticalBlockRaw.startsWith("Ошибка"))) {
                    String[] lines = theoreticalBlockRaw.split("\\R");
                    StringBuilder currentQuestion = new StringBuilder();
                    for (String line : lines) {
                        line = line.trim();
                        if (line.matches("^\\d+\\.\\s+.*")) {
                            if (currentQuestion.length() > 0) theoreticalQuestions.add(formatTextForHtml(currentQuestion.toString().trim()));
                            currentQuestion = new StringBuilder(line.replaceFirst("^\\d+\\.\\s*", "").trim());
                        } else if (currentQuestion.length() > 0 && !line.isEmpty()) {
                            currentQuestion.append(System.lineSeparator()).append(line);
                        }
                    }
                    if (currentQuestion.length() > 0) theoreticalQuestions.add(formatTextForHtml(currentQuestion.toString().trim()));
                }
                if (theoreticalQuestions.isEmpty()) theoreticalQuestions.add(theoreticalBlockRaw != null && (theoreticalBlockRaw.startsWith("Раздел") || theoreticalBlockRaw.startsWith("Ошибка")) ? theoreticalBlockRaw : "Теоретические вопросы не найдены.");
                ticket.setTheoreticalQuestions(theoreticalQuestions);

                String practicalTaskText = extractSection(cleanGeneratedText, "Практическая задача:", "---");
                if (practicalTaskText != null && (practicalTaskText.startsWith("Раздел") || practicalTaskText.startsWith("Ошибка"))) {
                    practicalTaskText = extractSection(cleanGeneratedText, "Практическая задача:", "Ответы:");
                }
                List<String> practicalTasks = new ArrayList<>();
                if (practicalTaskText != null && !(practicalTaskText.startsWith("Раздел") || practicalTaskText.startsWith("Ошибка"))) {
                    practicalTasks.add(formatTextForHtml(practicalTaskText.trim()));
                } else {
                    practicalTasks.add(practicalTaskText != null ? practicalTaskText : "Практическая задача не найдена.");
                }
                ticket.setPracticalTasks(practicalTasks);

            } catch (Exception e) {
                System.err.println("Ошибка парсинга для билета №" + i + ": " + e.getMessage());
                ticket.setTheoreticalQuestions(List.of(formatTextForHtml("Ошибка парсинга теор. вопросов.")));
                ticket.setPracticalTasks(List.of(formatTextForHtml("Ошибка парсинга практ. задач.")));
            }
            tickets.add(examTicketRepository.save(ticket));
        }
        return tickets;
    }

    private String extractSection(String text, String startMarker, String endMarker) {
        if (text == null || text.isBlank()) return "Исходный текст пуст.";
        try {
            int startIndex = text.toLowerCase().indexOf(startMarker.toLowerCase());
            if (startIndex == -1) return "Раздел '" + startMarker + "' не найден.";
            startIndex += startMarker.length();
            int endIndex;
            if (endMarker != null) {
                endIndex = text.toLowerCase().indexOf(endMarker.toLowerCase(), startIndex);
                if (endIndex == -1) endIndex = text.length();
            } else {
                endIndex = text.length();
            }
            return text.substring(startIndex, endIndex).trim();
        } catch (Exception e) {
            return "Ошибка извлечения раздела '" + startMarker + "'.";
        }
    }

    private List<String> extractList(String text, String startMarker, String endMarker) {
        String section = extractSection(text, startMarker, endMarker);
        if (section.startsWith("Раздел") || section.startsWith("Ошибка") || section.equals("Исходный текст пуст.")) {
            return List.of(section);
        }
        return Arrays.stream(section.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::formatTextForHtml)
                .collect(Collectors.toList());
    }
}