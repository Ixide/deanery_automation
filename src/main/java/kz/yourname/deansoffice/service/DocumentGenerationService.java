package kz.yourname.deansoffice.service; // Замените kz.yourname.deansoffice на ваш базовый пакет

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

    // Вспомогательный метод для замены переносов строк на <br/> для HTML
    private String formatTextForHtml(String text) {
        if (text == null || text.isBlank() || text.startsWith("Раздел") || text.startsWith("Ошибка")) {
            return text; // Не изменяем "Раздел не найден" или сообщения об ошибках парсинга
        }
        // Заменяем системные переносы и одиночные \n на <br/>
        return text.replace(System.lineSeparator(), "<br/>").replace("\n", "<br/>");
    }

    // Вспомогательный метод для форматирования списка строк для HTML
    private List<String> formatListForHtml(List<String> list) {
        if (list == null) return null;
        return list.stream()
                .map(this::formatTextForHtml) // Применяем форматирование к каждому элементу списка
                .collect(Collectors.toList());
    }

    public Syllabus generateSyllabus(SyllabusRequest request) {
        String prompt = String.format(
                "Создай силлабус для дисциплины \"%s\" для специальности \"%s\". " +
                        "Образовательные цели: %s. " +
                        "Силлабус должен включать: " +
                        "1. Описание курса. " +
                        "2. Темы лекций, соответствующие образовательным целям. " +
                        "3. Темы практических занятий, соответствующие образовательным целям. " +
                        "4. Список рекомендуемой литературы, актуальный для специальности (укажи не менее 5 источников с авторами и годом издания). " +
                        "5. Критерии оценки: опиши подробно возможные варианты системы оценивания (например, балльно-рейтинговая и зачет/незачет) с указанием весов для каждого вида контроля (экзамен, текущие задания, лабораторные и т.д.). " +
                        "Ответ дай в структурированном виде, например, используя маркеры для каждого раздела и подраздела. " +
                        "Раздел 'Критерии оценки' должен быть четко выделен.",
                request.getDisciplineName(),
                request.getSpecialty(),
                request.getEducationalGoals()
        );

        String generatedText = geminiService.generateContent(prompt);

        Syllabus syllabus = new Syllabus();
        syllabus.setDisciplineInfo(new DisciplineInfo(request.getDisciplineName(), request.getSpecialty(), request.getEducationalGoals()));
        // Убираем Markdown жирность из всего сгенерированного текста перед парсингом
        String cleanGeneratedText = generatedText.replace("**", "");
        syllabus.setGeneratedRawText(cleanGeneratedText); // Сохраняем очищенный сырой текст

        try {
            syllabus.setCourseDescription(formatTextForHtml(extractSection(cleanGeneratedText, "Описание курса:", "Темы лекций:")));
            syllabus.setLectureTopics(extractList(cleanGeneratedText, "Темы лекций:", "Темы практических занятий:"));
            syllabus.setPracticalTopics(extractList(cleanGeneratedText, "Темы практических занятий:", "Список рекомендуемой литературы:"));
            syllabus.setLiteratureList(extractList(cleanGeneratedText, "Список рекомендуемой литературы:", "Критерии оценки:"));

            String criteriaBlockText = extractSection(cleanGeneratedText, "Критерии оценки:", null); // до конца или до следующего явного раздела, если бы он был
            AssessmentCriteria criteria = new AssessmentCriteria();

            // Попытка извлечь тип системы оценивания из блока (очень упрощенно)
            if (criteriaBlockText != null && !(criteriaBlockText.startsWith("Раздел") || criteriaBlockText.startsWith("Ошибка"))) {
                String[] criteriaLines = criteriaBlockText.split("\\R");
                if (criteriaLines.length > 0) {
                    // Предполагаем, что тип системы может быть в первых строках
                    if (criteriaLines[0].toLowerCase().contains("балльно-рейтинговая")) {
                        criteria.setGradingSystemType("Балльно-рейтинговая");
                    } else if (criteriaLines[0].toLowerCase().contains("зачет/незачет")) {
                        criteria.setGradingSystemType("Зачет/незачет");
                    } else {
                        criteria.setGradingSystemType("Не указан (см. описание)");
                    }
                }
                criteria.setCriteriaDetails(Map.of("Экзамен", "40% (пример)", "Текущий контроль", "60% (пример)")); // Заглушка
                criteria.setDetailedBreakdown(Arrays.stream(criteriaLines)
                        .map(this::formatTextForHtml)
                        .collect(Collectors.toList()));
            } else {
                criteria.setGradingSystemType("Нет данных");
                criteria.setDetailedBreakdown(List.of(criteriaBlockText != null ? criteriaBlockText : "Блок критериев оценки не найден."));
            }
            syllabus.setAssessmentCriteria(criteria);

        } catch (Exception e) {
            System.err.println("Ошибка парсинга ответа Gemini для силлабуса: " + e.getMessage());
            if (syllabus.getCourseDescription() == null) syllabus.setCourseDescription(formatTextForHtml("Ошибка парсинга описания курса."));
            if (syllabus.getLectureTopics() == null || syllabus.getLectureTopics().isEmpty()) syllabus.setLectureTopics(List.of("Ошибка парсинга тем лекций."));
            // и т.д. для других полей
        }
        return syllabusRepository.save(syllabus);
    }

    public List<ExamTicket> generateExamTickets(ExamTicketRequest request) {
        List<ExamTicket> tickets = new ArrayList<>();
        String basePrompt = String.format(
                "Сгенерируй экзаменационный билет для дисциплины \"%s\". " +
                        "Билет должен соответствовать стандартному формату учебной документации. " +
                        "Темы для билетов: %s. " +
                        "Каждый билет должен содержать: " +
                        "1. Два теоретических вопроса (каждый вопрос должен начинаться с нового номера, например, '1. Текст вопроса', '2. Текст вопроса'). " +
                        "2. Одну практическую задачу. " +
                        "Вопросы и задачи должны быть четко сформулированы и разнообразны. " +
                        "Разделы 'Теоретические вопросы:' и 'Практическая задача:' должны быть явно выделены. После практической задачи поставь разделитель '---'.",
                request.getDisciplineName(),
                request.getTopicsToCover()
        );

        for (int i = 1; i <= request.getNumberOfTickets(); i++) {
            String ticketPrompt = basePrompt + String.format("\nЭто билет номер %d.", i);
            String generatedTextFromAI = geminiService.generateContent(ticketPrompt);

            // Убираем Markdown жирность из всего сгенерированного текста перед парсингом
            String cleanGeneratedText = generatedTextFromAI.replace("**", "");

            ExamTicket ticket = new ExamTicket();
            ticket.setDisciplineName(request.getDisciplineName());
            ticket.setTicketNumber(i);
            ticket.setGeneratedRawText(cleanGeneratedText);

            try {
                // Парсинг теоретических вопросов
                String theoreticalBlockRaw = extractSection(cleanGeneratedText, "Теоретические вопросы:", "Практическая задача:");
                List<String> theoreticalQuestions = new ArrayList<>();
                if (theoreticalBlockRaw != null && !(theoreticalBlockRaw.startsWith("Раздел") || theoreticalBlockRaw.startsWith("Ошибка"))) {
                    String[] lines = theoreticalBlockRaw.split("\\R");
                    StringBuilder currentQuestion = new StringBuilder();
                    for (String line : lines) {
                        line = line.trim();
                        if (line.matches("^\\d+\\.\\s+.*")) {
                            if (currentQuestion.length() > 0) {
                                theoreticalQuestions.add(formatTextForHtml(currentQuestion.toString().trim()));
                            }
                            currentQuestion = new StringBuilder(line.replaceFirst("^\\d+\\.\\s*", "").trim());
                        } else if (currentQuestion.length() > 0 && !line.isEmpty()) {
                            currentQuestion.append(System.lineSeparator()).append(line);
                        }
                    }
                    if (currentQuestion.length() > 0) {
                        theoreticalQuestions.add(formatTextForHtml(currentQuestion.toString().trim()));
                    }
                }

                if (theoreticalQuestions.isEmpty()) {
                    theoreticalQuestions.add(theoreticalBlockRaw != null && (theoreticalBlockRaw.startsWith("Раздел") || theoreticalBlockRaw.startsWith("Ошибка")) ? theoreticalBlockRaw : "Теоретические вопросы не найдены или не удалось распарсить.");
                }
                ticket.setTheoreticalQuestions(theoreticalQuestions);

                // Парсинг практической задачи
                String practicalTaskText = extractSection(cleanGeneratedText, "Практическая задача:", "---");
                if (practicalTaskText != null && (practicalTaskText.startsWith("Раздел") || practicalTaskText.startsWith("Ошибка") || practicalTaskText.equals("Исходный текст пуст."))) {
                    practicalTaskText = extractSection(cleanGeneratedText, "Практическая задача:", "Ответы:");
                }

                List<String> practicalTasks = new ArrayList<>();
                if (practicalTaskText != null && !(practicalTaskText.startsWith("Раздел") || practicalTaskText.startsWith("Ошибка") || practicalTaskText.equals("Исходный текст пуст."))) {
                    practicalTasks.add(formatTextForHtml(practicalTaskText.trim()));
                } else {
                    practicalTasks.add(practicalTaskText != null ? practicalTaskText : "Практическая задача не найдена.");
                }
                ticket.setPracticalTasks(practicalTasks);

            } catch (Exception e) {
                System.err.println("Критическая ошибка парсинга ответа Gemini для экзаменационного билета №" + i + ": " + e.getMessage());
                ticket.setTheoreticalQuestions(List.of(formatTextForHtml("Ошибка парсинга теоретических вопросов. См. сырой текст.")));
                ticket.setPracticalTasks(List.of(formatTextForHtml("Ошибка парсинга практических задач. См. сырой текст.")));
            }
            tickets.add(examTicketRepository.save(ticket));
        }
        return tickets;
    }

    private String extractSection(String text, String startMarker, String endMarker) {
        if (text == null || text.isBlank()) return "Исходный текст пуст.";
        try {
            // Маркеры уже должны быть "чистыми" (без **), т.к. мы их убрали из generatedText
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
            System.err.println("Исключение в extractSection при поиске '" + startMarker + "' в тексте (...): " + e.getMessage());
            return "Ошибка извлечения раздела '" + startMarker + "'.";
        }
    }

    private List<String> extractList(String text, String startMarker, String endMarker) {
        String section = extractSection(text, startMarker, endMarker);
        if (section.startsWith("Раздел") || section.startsWith("Ошибка") || section.equals("Исходный текст пуст.")) {
            return List.of(section);
        }
        // Для HTML форматирования списков, применяем formatTextForHtml к каждому элементу
        return Arrays.stream(section.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::formatTextForHtml) // Форматируем каждую строку списка для HTML
                .collect(Collectors.toList());
    }

    // Метод extractListBetween не используется в текущей версии generateExamTickets,
    // так как логика парсинга теоретических вопросов была встроена напрямую.
    // Если он нужен для других целей, его можно оставить или удалить.
    /*
    private List<String> extractListBetween(String text, String startMarker, String endMarker) {
        // ... (предыдущая реализация, если нужна)
    }
    */
}