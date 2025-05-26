package kz.yourname.deansoffice.service; // Замените kz.yourname.deansoffice на ваш пакет

import kz.yourname.deansoffice.dto.ExamTicketRequest;
import kz.yourname.deansoffice.dto.SyllabusRequest;
import kz.yourname.deansoffice.model.*;
import kz.yourname.deansoffice.repository.ExamTicketRepository;
import kz.yourname.deansoffice.repository.SyllabusRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private String formatTextForHtmlDisplay(String text) {
        if (text == null || text.isBlank() || text.contains("не сгенерирован") || text.contains("не найден") || text.contains("ошибка парсинга")) {
            return text;
        }
        return text.replace(System.lineSeparator(), "<br/>").replace("\n", "<br/>");
    }

    private List<String> formatListForHtmlDisplay(List<String> list) {
        if (list == null) return new ArrayList<>();
        return list.stream()
                .map(this::formatTextForHtmlDisplay)
                .collect(Collectors.toList());
    }

    public Syllabus generateSyllabus(SyllabusRequest request) {
        String prompt = String.format(
                "Создай детальный контент для силлабуса по дисциплине \"%s\" для специальности \"%s\". " +
                        "Образовательные цели: %s. " +
                        "Предоставь информацию в следующем формате, четко разделяя секции указанными маркерами (каждый маркер и каждая новая запись в списке должны быть на новой строке):%n%n" +
                        "МАРКЕР_ОПИСАНИЕ_КУРСА_НАЧАЛО%n[Здесь подробное описание курса, его основные цели и задачи в рамках образовательной программы]%nМАРКЕР_ОПИСАНИЕ_КУРСА_КОНЕЦ%n%n" +
                        "МАРКЕР_РЕЗУЛЬТАТЫ_ОБУЧЕНИЯ_НАЧАЛО%n[Здесь список результатов обучения (РО) в формате 'РО1: формулировка'. Каждый РО на новой строке.]%nМАРКЕР_РЕЗУЛЬТАТЫ_ОБУЧЕНИЯ_КОНЕЦ%n%n" +
                        "МАРКЕР_КЛЮЧЕВЫЕ_ИНДИКАТОРЫ_РО_НАЧАЛО%n[Здесь список ключевых индикаторов РО, например, для конкретных РО, в формате 'КодИндикатора: формулировка'. Каждый на новой строке.]%nМАРКЕР_КЛЮЧЕВЫЕ_ИНДИКАТОРЫ_РО_КОНЕЦ%n%n" +
                        "МАРКЕР_ДЕТАЛЬНЫЕ_ИНДИКАТОРЫ_РО_НАЧАЛО%n[Здесь ПОЛНЫЙ список индикаторов достижения ВСЕХ РО, в формате 'КодИндикатора: формулировка'. Каждый на новой строке.]%nМАРКЕР_ДЕТАЛЬНЫЕ_ИНДИКАТОРЫ_РО_КОНЕЦ%n%n" +
                        "МАРКЕР_ПРЕРЕКВИЗИТЫ_НАЧАЛО%n[Здесь текст пререквизитов для данной дисциплины. Если их несколько, каждый на новой строке.]%nМАРКЕР_ПРЕРЕКВИЗИТЫ_КОНЕЦ%n%n" +
                        "МАРКЕР_ПОСТРЕКВИЗИТЫ_НАЧАЛО%n[Здесь текст постреквизитов для данной дисциплины. Если их несколько, каждый на новой строке.]%nМАРКЕР_ПОСТРЕКВИЗИТЫ_КОНЕЦ%n%n" +
                        "МАРКЕР_ТЕМАТИЧЕСКИЙ_ПЛАН_НАЧАЛО%n" +
                        "[Для каждого модуля укажи:%n" +
                        "МОДУЛЬ [Номер модуля]: [Название модуля]%n" +
                        "  Тема [Номер темы в модуле, например 1.1]: [ОБЩЕЕ НАЗВАНИЕ ТЕМЫ]%n" +
                        "    Тема лекции: [Уточняющее название/содержание темы лекции]%n" +
                        "    Лекция: [детальное содержание лекции по теме, может быть несколько предложений]%n" +
                        "    Тема практического занятия: [Уточняющее название/содержание темы практического занятия]%n" +
                        "    Практика: [детальное содержание практики по теме, может быть несколько предложений]%n" +
                        "    Задания: [список общих заданий по теме, пункты через ';']%n" +
                        "    Тема СРСП: [Название темы СРСП для этой темы]%n" +
                        "    Задания СРСП: [список заданий СРСП, пункты через ';']%n" +
                        "    Часы лекций: [количество часов для этой темы]%n" +
                        "    Часы практик: [количество часов для этой темы]%n" +
                        "    Часы СРСП/СРС: [количество часов для этой темы]%n" +
                        "    РО: [коды РО, покрываемые этой темой, через запятую, например РО1,РО2.1]%n" +
                        "  КОНЕЦ_ТЕМЫ%n" +
                        "(Повтори блок 'Тема...КОНЕЦ_ТЕМЫ' для каждой темы в модуле. Затем начни следующий модуль с 'МОДУЛЬ...')%n" +
                        "МАРКЕР_ТЕМАТИЧЕСКИЙ_ПЛАН_КОНЕЦ%n%n" +
                        "МАРКЕР_ЛИТЕРАТУРА_НАЧАЛО%n[Здесь список основной и дополнительной литературы.]%nМАРКЕР_ЛИТЕРАТУРА_КОНЕЦ%n%n" +
                        "МАРКЕР_ИНТЕРНЕТ_РЕСУРСЫ_НАЧАЛО%n[Здесь список интернет-ресурсов.]%nМАРКЕР_ИНТЕРНЕТ_РЕСУРСЫ_КОНЕЦ%n%n" +
                        "МАРКЕР_ПО_НАЧАЛО%n[Здесь список программного обеспечения.]%nМАРКЕР_ПО_КОНЕЦ%n%n" +
                        "МАРКЕР_ПОЛИТИКА_ДИСЦИПЛИНЫ_НАЧАЛО%n[Здесь текст политики дисциплины.]%nМАРКЕР_ПОЛИТИКА_ДИСЦИПЛИНЫ_КОНЕЦ%n%n" +
                        "МАРКЕР_КРИТЕРИИ_ОЦЕНКИ_НАЧАЛО%n[Здесь описание критериев оценки.]%nМАРКЕР_КРИТЕРИИ_ОЦЕНКИ_КОНЕЦ%n%n" +
                        "МАРКЕР_ЭКЗАМЕНАЦИОННЫЕ_ВОПРОСЫ_НАЧАЛО%n[Здесь список экзаменационных вопросов. Сгенерируй минимум 20 вопросов.]%nМАРКЕР_ЭКЗАМЕНАЦИОННЫЕ_ВОПРОСЫ_КОНЕЦ%n",
                request.getDisciplineName(),
                request.getSpecialty(),
                request.getEducationalGoals()
        );

        String generatedTextFromAI = geminiService.generateContent(prompt);
        Syllabus syllabus = new Syllabus();
        DisciplineInfo disciplineInfo = new DisciplineInfo(request.getDisciplineName(), request.getSpecialty(), request.getEducationalGoals());
        syllabus.setDisciplineInfo(disciplineInfo);
        syllabus.setGeneratedRawText(generatedTextFromAI);

        syllabus.setCourseCode(request.getDisciplineName() != null ?
                request.getDisciplineName().replaceAll("[^a-zA-Zа-яА-Я]", "").substring(0, Math.min(request.getDisciplineName().replaceAll("[^a-zA-Zа-яА-Я]", "").length(), 3)).toUpperCase() + "3206"
                : "[КОД]");

        syllabus.setCourseDescription(formatTextForHtmlDisplay(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ОПИСАНИЕ_КУРСА_НАЧАЛО", "МАРКЕР_ОПИСАНИЕ_КУРСА_КОНЕЦ", "Описание курса не сгенерировано.")));
        syllabus.setLearningOutcomes(formatListForHtmlDisplay(parseToList(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_РЕЗУЛЬТАТЫ_ОБУЧЕНИЯ_НАЧАЛО", "МАРКЕР_РЕЗУЛЬТАТЫ_ОБУЧЕНИЯ_КОНЕЦ", "Результаты обучения не сгенерированы."))));

        List<Map<String, String>> keyIndicators = new ArrayList<>();
        String keyRoIndicatorsText = extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_КЛЮЧЕВЫЕ_ИНДИКАТОРЫ_РО_НАЧАЛО", "МАРКЕР_КЛЮЧЕВЫЕ_ИНДИКАТОРЫ_РО_КОНЕЦ", "Ключевые индикаторы РО не сгенерированы.");
        for (String line : parseToList(keyRoIndicatorsText)) {
            String[] parts = line.split(":", 2); Map<String, String> pair = new HashMap<>();
            if (parts.length == 2) { pair.put("code", formatTextForHtmlDisplay(parts[0].trim())); pair.put("indicator", formatTextForHtmlDisplay(parts[1].trim()));}
            else { pair.put("indicator", formatTextForHtmlDisplay(line.trim()));}
            keyIndicators.add(pair);
        }
        syllabus.setKeyRoIndicators(keyIndicators);

        List<Map<String, String>> detailedIndicators = new ArrayList<>();
        String detailedRoIndicatorsText = extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ДЕТАЛЬНЫЕ_ИНДИКАТОРЫ_РО_НАЧАЛО", "МАРКЕР_ДЕТАЛЬНЫЕ_ИНДИКАТОРЫ_РО_КОНЕЦ", "Детальные индикаторы РО не сгенерированы.");
        for (String line : parseToList(detailedRoIndicatorsText)) {
            String[] parts = line.split(":", 2); Map<String, String> pair = new HashMap<>();
            if (parts.length == 2) { pair.put("code", formatTextForHtmlDisplay(parts[0].trim())); pair.put("indicator", formatTextForHtmlDisplay(parts[1].trim()));}
            else { pair.put("indicator", formatTextForHtmlDisplay(line.trim()));}
            detailedIndicators.add(pair);
        }
        syllabus.setDetailedRoIndicators(detailedIndicators);

        syllabus.setPrerequisites(formatTextForHtmlDisplay(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ПРЕРЕКВИЗИТЫ_НАЧАЛО", "МАРКЕР_ПРЕРЕКВИЗИТЫ_КОНЕЦ", "Пререквизиты не сгенерированы.")));
        syllabus.setPostrequisites(formatTextForHtmlDisplay(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ПОСТРЕКВИЗИТЫ_НАЧАЛО", "МАРКЕР_ПОСТРЕКВИЗИТЫ_КОНЕЦ", "Постреквизиты не сгенерированы.")));

        syllabus.setThematicPlan(parseThematicPlan(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ТЕМАТИЧЕСКИЙ_ПЛАН_НАЧАЛО", "МАРКЕР_ТЕМАТИЧЕСКИЙ_ПЛАН_КОНЕЦ", "Тематический план не сгенерирован.")));

        syllabus.setHigherSchoolName(syllabus.getHigherSchoolName() !=null ? syllabus.getHigherSchoolName() : "[Название высшей школы]");
        syllabus.setLectorNameAndPosition(syllabus.getLectorNameAndPosition() != null ? syllabus.getLectorNameAndPosition() : "[ФИО лектора, должность]");
        syllabus.setLectorEmailAndPhone(syllabus.getLectorEmailAndPhone() != null ? syllabus.getLectorEmailAndPhone() : "[email], [телефон]");

        if (syllabus.getThematicPlan() != null) {
            int totalLecturesOverall = 0;
            int totalSeminarsOverall = 0;
            int totalSrspSrsOverall = 0;
            for (ThematicPlanModule module : syllabus.getThematicPlan()) {
                if (module.getTopics() != null) {
                    for (ThematicPlanTopic topic : module.getTopics()) {
                        totalLecturesOverall += parseHoursSilent(topic.getLectureHours());
                        totalSeminarsOverall += parseHoursSilent(topic.getSeminarHours());
                        totalSrspSrsOverall += parseHoursSilent(topic.getSrspAndSrsHours());
                    }
                }
            }
            syllabus.setLecturesHours(String.valueOf(totalLecturesOverall));
            syllabus.setSeminarsHours(String.valueOf(totalSeminarsOverall));
            syllabus.setSrspHours(String.valueOf(totalSrspSrsOverall));
            syllabus.setSrsHours("");
            syllabus.setTotalHours(String.valueOf(totalLecturesOverall + totalSeminarsOverall + totalSrspSrsOverall));
        }
        syllabus.setFinalControlType(syllabus.getFinalControlType() != null ? syllabus.getFinalControlType() : "[Форма контроля]");

        syllabus.setLiteratureList(formatListForHtmlDisplay(parseToList(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ЛИТЕРАТУРА_НАЧАЛО", "МАРКЕР_ЛИТЕРАТУРА_КОНЕЦ", "Литература не сгенерирована."))));
        syllabus.setInternetResources(formatListForHtmlDisplay(parseToList(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ИНТЕРНЕТ_РЕСУРСЫ_НАЧАЛО", "МАРКЕР_ИНТЕРНЕТ_РЕСУРСЫ_КОНЕЦ", "Интернет-ресурсы не сгенерированы."))));
        syllabus.setSoftwareUsed(formatListForHtmlDisplay(parseToList(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ПО_НАЧАЛО", "МАРКЕР_ПО_КОНЕЦ", "ПО не сгенерировано."))));
        syllabus.setDisciplinePolicyText(formatTextForHtmlDisplay(extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ПОЛИТИКА_ДИСЦИПЛИНЫ_НАЧАЛО", "МАРКЕР_ПОЛИТИКА_ДИСЦИПЛИНЫ_КОНЕЦ", "Политика дисциплины не сгенерирована.")));

        AssessmentCriteria criteria = new AssessmentCriteria();
        String criteriaText = extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_КРИТЕРИИ_ОЦЕНКИ_НАЧАЛО", "МАРКЕР_КРИТЕРИИ_ОЦЕНКИ_КОНЕЦ", "Критерии оценки не сгенерированы.");
        criteria.setDetailedBreakdown(formatListForHtmlDisplay(parseToList(criteriaText)));
        criteria.setGradingSystemType("См. описание");
        syllabus.setAssessmentCriteria(criteria);

        String examQuestionsStartMarker = "МАРКЕР_ЭКЗАМЕНАЦИОННЫЕ_ВОПРОСЫ_НАЧАЛО";
        String examQuestionsEndMarker = "МАРКЕР_ЭКЗАМЕНАЦИОННЫЕ_ВОПРОСЫ_КОНЕЦ";
        String defaultExamText = "Экзаменационные вопросы не сгенерированы.";

        System.out.println("--- ОТЛАДКА: Поиск маркеров для экзаменационных вопросов ---");
        System.out.println("Ищем начальный маркер: [" + examQuestionsStartMarker + "]");
        int startIndex = generatedTextFromAI.toLowerCase().indexOf(examQuestionsStartMarker.toLowerCase());
        System.out.println("Индекс начального маркера: " + startIndex);

        if (startIndex != -1) {
            int actualStartIndex = startIndex + examQuestionsStartMarker.length();
            System.out.println("Ищем конечный маркер: [" + examQuestionsEndMarker + "] после индекса " + actualStartIndex);
            int endIndex = generatedTextFromAI.toLowerCase().indexOf(examQuestionsEndMarker.toLowerCase(), actualStartIndex);
            System.out.println("Индекс конечного маркера: " + endIndex);
            if (endIndex != -1) {
                System.out.println("Оба маркера для экзаменационных вопросов найдены!");
            } else {
                System.out.println("КОНЕЧНЫЙ МАРКЕР для экзаменационных вопросов НЕ НАЙДЕН!");
            }
        } else {
            System.out.println("НАЧАЛЬНЫЙ МАРКЕР для экзаменационных вопросов НЕ НАЙДЕН!");
        }
        System.out.println("--- КОНЕЦ ОТЛАДКИ: Поиск маркеров ---");
        syllabus.setExaminationQuestions(formatListForHtmlDisplay(parseToList(extractSectionByMarkers(generatedTextFromAI, examQuestionsStartMarker, examQuestionsEndMarker, defaultExamText))));

        System.out.println("--- generateSyllabus: Итоговый объект syllabus.thematicPlan ---");
        if (syllabus.getThematicPlan() != null) {
            System.out.println("Количество модулей: " + syllabus.getThematicPlan().size());
            for (ThematicPlanModule module : syllabus.getThematicPlan()) {
                System.out.println("  Модуль: " + module.getModuleNumberAndName());
                if (module.getTopics() != null) {
                    System.out.println("    Тем в модуле: " + module.getTopics().size());
                    for (ThematicPlanTopic topic : module.getTopics()) {
                        System.out.println("      Тема " + topic.getTopicNumberInModule() + ": " + topic.getGeneralTopicTitle());
                        System.out.println("        Тема лекции: " + topic.getLectureTheme());
                        System.out.println("        Содержание лекции: " + topic.getLectureContent());
                        System.out.println("        Тема практики: " + topic.getPracticalTheme());
                        System.out.println("        Содержание практики: " + topic.getPracticalContent());
                        System.out.println("        Задания: " + topic.getTasks());
                        System.out.println("        Тема СРСП: " + topic.getSrspTheme());
                        System.out.println("        Задания СРСП: " + topic.getSrspTasksList());
                        System.out.println("        Часы (Л/П/СРС): " + topic.getLectureHours() + "/" + topic.getSeminarHours() + "/" + topic.getSrspAndSrsHours());
                        System.out.println("        РО: " + topic.getRoCovered());
                    }
                } else { System.out.println("    Темы в этом модуле: null"); }
            }
        } else { System.out.println("syllabus.thematicPlan is null"); }
        System.out.println("--- generateSyllabus: Конец отладки thematicPlan ---");

        return syllabusRepository.save(syllabus);
    }

    private String extractSectionByMarkers(String text, String startMarker, String endMarker, String defaultText) {
        if (text == null || text.isBlank()) return defaultText;
        try {
            String lowerText = text.toLowerCase();
            String lowerStartMarker = startMarker.toLowerCase();
            String lowerEndMarker = endMarker.toLowerCase();
            int startIndexOriginal = lowerText.indexOf(lowerStartMarker);
            if (startIndexOriginal == -1) {
                System.err.println("extractSectionByMarkers: Начальный маркер '" + startMarker + "' не найден.");
                return defaultText + " (не найден нач. маркер: " + startMarker + ")";
            }
            int actualStartIndex = startIndexOriginal + startMarker.length();
            int endIndexOriginal = lowerText.indexOf(lowerEndMarker, actualStartIndex);
            if (endIndexOriginal == -1) {
                System.err.println("extractSectionByMarkers: Конечный маркер '" + endMarker + "' не найден после начального.");
                return defaultText + " (не найден кон. маркер: " + endMarker + ")";
            }
            return text.substring(actualStartIndex, endIndexOriginal).trim();
        } catch (Exception e) {
            System.err.println("Ошибка извлечения секции: " + startMarker + " -> " + endMarker + ": " + e.getMessage());
            return defaultText + " (ошибка парсинга)";
        }
    }

    private List<String> parseToList(String textBlock) {
        if (textBlock == null || textBlock.isBlank() || textBlock.contains("не сгенерирован") || textBlock.contains("не найден") || textBlock.contains("ошибка парсинга")) {
            return List.of(textBlock != null ? textBlock : "");
        }
        return Arrays.stream(textBlock.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<ThematicPlanModule> parseThematicPlan(String planText) {
        System.out.println("\n--- НАЧАЛО ОТЛАДКИ parseThematicPlan ---");
        // System.out.println("Полученный текст для парсинга тематического плана:\n\"\"\"\n" + planText + "\n\"\"\"");

        List<ThematicPlanModule> modules = new ArrayList<>();
        if (planText == null || planText.isBlank() || planText.contains("не сгенерирован") || planText.contains("не найден") || planText.contains("ошибка парсинга")) {
            ThematicPlanModule errorModule = new ThematicPlanModule();
            String errorMessage = planText != null ? planText : "Тематический план не удалось загрузить.";
            errorModule.setModuleNumberAndName(formatTextForHtmlDisplay(errorMessage));
            errorModule.setTopics(new ArrayList<>());
            modules.add(errorModule);
            System.out.println("ОШИБКА/ПУСТО в parseThematicPlan: " + errorMessage);
            System.out.println("--- КОНЕЦ ОТЛАДКИ parseThematicPlan ---\n");
            return modules;
        }

        String[] lines = planText.split("\\R");
        ThematicPlanModule currentModule = null;
        ThematicPlanTopic currentTopic = null;

        Pattern modulePattern = Pattern.compile("^МОДУЛЬ\\s*([\\d\\w\\s().,-]+):\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern topicPattern = Pattern.compile("^\\s*Тема\\s*([\\d.]+):(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern lectureThemePattern = Pattern.compile("^\\s*Тема лекции:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern lectureContentPattern = Pattern.compile("^\\s*Лекция:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern practicalThemePattern = Pattern.compile("^\\s*Тема практического занятия:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern practicalContentPattern = Pattern.compile("^\\s*Практика:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern tasksPattern = Pattern.compile("^\\s*Задания:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern srspThemePattern = Pattern.compile("^\\s*Тема СРСП:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern srspTasksPattern = Pattern.compile("^\\s*Задания СРСП:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern lectureHoursPattern = Pattern.compile("^\\s*Часы лекций:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern practiceHoursPattern = Pattern.compile("^\\s*Часы практик:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern srspSrsHoursPattern = Pattern.compile("^\\s*Часы СРСП/СРС:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern roPattern = Pattern.compile("^\\s*РО:\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern endTopicPattern = Pattern.compile("^\\s*КОНЕЦ_ТЕМЫ\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        String currentSubSectionType = null;

        for (String line : lines) {
            line = line.trim();
            System.out.println("Парсинг строки: \"" + line + "\"");
            Matcher m;

            m = modulePattern.matcher(line);
            if (m.matches()) {
                System.out.println("  НАЙДЕН МОДУЛЬ: " + m.group(1).trim() + " - " + m.group(2).trim());
                if (currentModule != null && currentTopic != null) { currentModule.getTopics().add(currentTopic); System.out.println("    Добавлена предыдущая тема '" + (currentTopic.getTopicNumberInModule() != null ? currentTopic.getTopicNumberInModule() : "N/A") + "' в модуль: " + currentModule.getModuleNumberAndName());}
                if (currentModule != null && ((currentModule.getTopics()!=null && !currentModule.getTopics().isEmpty()) || (currentModule.getModuleNumberAndName()!=null && !currentModule.getModuleNumberAndName().contains("не удалось загрузить"))) ) {
                    ThematicPlanModule moduleToAdd = currentModule;
                    boolean moduleExists = modules.stream().anyMatch(mod ->
                            mod.getModuleNumberAndName() != null &&
                                    moduleToAdd.getModuleNumberAndName() != null &&
                                    mod.getModuleNumberAndName().equals(moduleToAdd.getModuleNumberAndName())
                    );
                    if(!moduleExists) {
                        modules.add(currentModule);
                        System.out.println("  Добавлен предыдущий модуль '" + currentModule.getModuleNumberAndName() + "' в список.");
                    }
                }
                currentModule = new ThematicPlanModule();
                currentModule.setModuleNumberAndName("Модуль " + m.group(1).trim() + ": " + m.group(2).trim());
                currentModule.setTopics(new ArrayList<>());
                currentTopic = null;
                currentSubSectionType = null;
                continue;
            }

            m = topicPattern.matcher(line);
            if (m.matches() && currentModule != null) {
                String topicNumber = m.group(1).trim();
                String generalTopicTitle = m.group(2).trim();
                System.out.println("  НАЙДЕНО НАЧАЛО ТЕМЫ (по паттерну topicPattern): Номер '" + topicNumber + "', Общее название: '" + generalTopicTitle + "'");
                if (currentTopic != null) { currentModule.getTopics().add(currentTopic); System.out.println("    Добавлена предыдущая тема '" + currentTopic.getTopicNumberInModule() + "' в модуль: " + currentModule.getModuleNumberAndName());}
                currentTopic = new ThematicPlanTopic();
                currentTopic.setTopicNumberInModule(topicNumber);
                currentTopic.setGeneralTopicTitle(generalTopicTitle);
                currentSubSectionType = null;
                continue;
            }

            if (endTopicPattern.matcher(line).matches() && currentTopic != null && currentModule != null) {
                System.out.println("  НАЙДЕН КОНЕЦ_ТЕМЫ для темы " + currentTopic.getTopicNumberInModule());
                currentModule.getTopics().add(currentTopic);
                currentTopic = null;
                currentSubSectionType = null;
                continue;
            }

            if (currentTopic != null) {
                m = lectureThemePattern.matcher(line);  if (m.matches()) { System.out.println("    Тема лекции: " + m.group(1).trim()); currentTopic.setLectureTheme(m.group(1).trim()); currentSubSectionType = null; continue; }
                m = lectureContentPattern.matcher(line);if (m.matches()) { System.out.println("    Лекция (содержание): " + m.group(1).trim()); currentTopic.getLectureContent().add(m.group(1).trim()); currentSubSectionType = "lecture_content"; continue; }
                m = practicalThemePattern.matcher(line);if (m.matches()) { System.out.println("    Тема практики: " + m.group(1).trim()); currentTopic.setPracticalTheme(m.group(1).trim()); currentSubSectionType = null; continue; }
                m = practicalContentPattern.matcher(line);if (m.matches()) { System.out.println("    Практика (содержание): " + m.group(1).trim()); currentTopic.getPracticalContent().add(m.group(1).trim()); currentSubSectionType = "practical_content"; continue; }
                m = tasksPattern.matcher(line);         if (m.matches()) { System.out.println("    Задания: " + m.group(1)); currentTopic.getTasks().addAll(Arrays.asList(m.group(1).split("\\s*;\\s*"))); currentSubSectionType = "tasks"; continue; }
                m = srspThemePattern.matcher(line);     if (m.matches()) { System.out.println("    Тема СРСП: " + m.group(1).trim()); currentTopic.setSrspTheme(m.group(1).trim()); currentSubSectionType = null; continue; }
                m = srspTasksPattern.matcher(line);     if (m.matches()) { System.out.println("    Задания СРСП: " + m.group(1)); currentTopic.getSrspTasksList().addAll(Arrays.asList(m.group(1).split("\\s*;\\s*"))); currentSubSectionType = "srsp_tasks"; continue; }
                m = lectureHoursPattern.matcher(line);  if (m.matches()) { System.out.println("    Часы лекций: " + m.group(1).trim()); currentTopic.setLectureHours(m.group(1).trim()); currentSubSectionType = null; continue; }
                m = practiceHoursPattern.matcher(line); if (m.matches()) { System.out.println("    Часы практик: " + m.group(1).trim()); currentTopic.setSeminarHours(m.group(1).trim()); currentSubSectionType = null; continue; }
                m = srspSrsHoursPattern.matcher(line);  if (m.matches()) { System.out.println("    Часы СРСП/СРС: " + m.group(1).trim()); currentTopic.setSrspAndSrsHours(m.group(1).trim()); currentSubSectionType = null; continue; }
                m = roPattern.matcher(line);            if (m.matches()) { System.out.println("    РО: " + m.group(1)); currentTopic.getRoCovered().addAll(Arrays.asList(m.group(1).split("\\s*,\\s*"))); currentSubSectionType = null; continue; }

                if (!line.isEmpty() && currentSubSectionType != null) {
                    System.out.println("    Продолжение для '" + currentSubSectionType + "': " + line);
                    switch (currentSubSectionType) {
                        case "lecture_content":
                            if (!currentTopic.getLectureContent().isEmpty()) { currentTopic.getLectureContent().set(currentTopic.getLectureContent().size() - 1, currentTopic.getLectureContent().get(currentTopic.getLectureContent().size() - 1) + System.lineSeparator() + line);
                            } else { currentTopic.getLectureContent().add(line); } break;
                        case "practical_content":
                            if (!currentTopic.getPracticalContent().isEmpty()) { currentTopic.getPracticalContent().set(currentTopic.getPracticalContent().size() - 1, currentTopic.getPracticalContent().get(currentTopic.getPracticalContent().size() - 1) + System.lineSeparator() + line);
                            } else { currentTopic.getPracticalContent().add(line); } break;
                        case "tasks":
                            if (!currentTopic.getTasks().isEmpty()) { currentTopic.getTasks().set(currentTopic.getTasks().size() - 1, currentTopic.getTasks().get(currentTopic.getTasks().size() - 1) + " " + line);
                            } else { currentTopic.getTasks().add(line); } break;
                        case "srsp_tasks":
                            if (!currentTopic.getSrspTasksList().isEmpty()) { currentTopic.getSrspTasksList().set(currentTopic.getSrspTasksList().size() - 1, currentTopic.getSrspTasksList().get(currentTopic.getSrspTasksList().size() - 1) + " " + line);
                            } else { currentTopic.getSrspTasksList().add(line); } break;
                    }
                }
            }
        }
        if (currentModule != null && currentTopic != null) { currentModule.getTopics().add(currentTopic); System.out.println("Добавлена последняя тема в модуль (после цикла): " + (currentTopic.getTopicNumberInModule() != null ? currentTopic.getTopicNumberInModule() : "N/A") );}
        if (currentModule != null && ((currentModule.getTopics()!=null && !currentModule.getTopics().isEmpty()) || (currentModule.getModuleNumberAndName()!=null && !currentModule.getModuleNumberAndName().contains("не удалось загрузить"))) ) {
            ThematicPlanModule finalModuleToAdd = currentModule;
            boolean moduleExists = modules.stream().anyMatch(mod ->
                    mod.getModuleNumberAndName() != null &&
                            finalModuleToAdd.getModuleNumberAndName() != null &&
                            mod.getModuleNumberAndName().equals(finalModuleToAdd.getModuleNumberAndName())
            );
            if(!moduleExists) {
                modules.add(currentModule);
                System.out.println("Добавлен последний модуль в список (после цикла): " + currentModule.getModuleNumberAndName());
            }
        }

        for (ThematicPlanModule module : modules) {
            module.setModuleNumberAndName(formatTextForHtmlDisplay(module.getModuleNumberAndName()));
            if (module.getTopics() != null) {
                for (ThematicPlanTopic topic : module.getTopics()) {
                    topic.setGeneralTopicTitle(formatTextForHtmlDisplay(topic.getGeneralTopicTitle()));
                    topic.setLectureTheme(formatTextForHtmlDisplay(topic.getLectureTheme()));
                    topic.setPracticalTheme(formatTextForHtmlDisplay(topic.getPracticalTheme()));
                    topic.setTasks(formatListForHtmlDisplay(topic.getTasks()));
                    topic.setSrspTheme(formatTextForHtmlDisplay(topic.getSrspTheme()));
                    topic.setSrspTasksList(formatListForHtmlDisplay(topic.getSrspTasksList()));
                    topic.setLectureContent(formatListForHtmlDisplay(topic.getLectureContent()));
                    topic.setPracticalContent(formatListForHtmlDisplay(topic.getPracticalContent()));
                }
            }
        }
        System.out.println("--- КОНЕЦ ОТЛАДКИ parseThematicPlan, модулей обработано: " + modules.size() + " ---");
        if (!modules.isEmpty() && modules.get(0).getTopics() != null && !modules.get(0).getModuleNumberAndName().contains("не удалось загрузить")) {
            System.out.println("Тем в первом обработанном модуле: " + modules.get(0).getTopics().size());
        }
        return modules;
    }

    public List<ExamTicket> generateExamTickets(ExamTicketRequest request) {
        List<ExamTicket> tickets = new ArrayList<>();
        String basePrompt = String.format(
                "Сгенерируй экзаменационный билет для дисциплины \"%s\". " +
                        "Темы для билетов: %s. " +
                        "Каждый билет должен содержать: " +
                        "МАРКЕР_ТЕОРЕТИЧЕСКИЕ_ВОПРОСЫ_НАЧАЛО%n1. [Текст первого теоретического вопроса]%n2. [Текст второго теоретического вопроса]%nМАРКЕР_ТЕОРЕТИЧЕСКИЕ_ВОПРОСЫ_КОНЕЦ%n" +
                        "МАРКЕР_ПРАКТИЧЕСКАЯ_ЗАДАЧА_НАЧАЛО%n[Текст практической задачи]%nМАРКЕР_ПРАКТИЧЕСКАЯ_ЗАДАЧА_КОНЕЦ%n" +
                        "Убедись, что каждый вопрос и задача четко сформулированы и разделены указанными маркерами.",
                request.getDisciplineName(),
                request.getTopicsToCover()
        );

        for (int i = 1; i <= request.getNumberOfTickets(); i++) {
            String ticketPrompt = basePrompt + String.format("\nЭто билет номер %d.", i);
            String generatedTextFromAI = geminiService.generateContent(ticketPrompt);

            ExamTicket ticket = new ExamTicket();
            ticket.setDisciplineName(request.getDisciplineName());
            ticket.setTicketNumber(i);
            ticket.setGeneratedRawText(generatedTextFromAI);

            try {
                String theoreticalBlockRaw = extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ТЕОРЕТИЧЕСКИЕ_ВОПРОСЫ_НАЧАЛО", "МАРКЕР_ТЕОРЕТИЧЕСКИЕ_ВОПРОСЫ_КОНЕЦ", "Теоретические вопросы не найдены.");
                ticket.setTheoreticalQuestions(
                        formatListForHtmlDisplay(
                                parseToList(theoreticalBlockRaw).stream()
                                        .map(q -> q.replaceFirst("^\\d+\\.\\s*", "").trim())
                                        .collect(Collectors.toList())
                        )
                );

                String practicalTaskText = extractSectionByMarkers(generatedTextFromAI, "МАРКЕР_ПРАКТИЧЕСКАЯ_ЗАДАЧА_НАЧАЛО", "МАРКЕР_ПРАКТИЧЕСКАЯ_ЗАДАЧА_КОНЕЦ", "Практическая задача не найдена.");
                ticket.setPracticalTasks(List.of(formatTextForHtmlDisplay(practicalTaskText)));

            } catch (Exception e) {
                System.err.println("Критическая ошибка парсинга ответа Gemini для экзаменационного билета №" + i + ": " + e.getMessage());
                ticket.setTheoreticalQuestions(List.of(formatTextForHtmlDisplay("Ошибка парсинга теоретических вопросов.")));
                ticket.setPracticalTasks(List.of(formatTextForHtmlDisplay("Ошибка парсинга практической задачи.")));
            }
            tickets.add(examTicketRepository.save(ticket));
        }
        return tickets;
    }

    private int parseHoursSilent(String hoursStr) {
        if (hoursStr == null || hoursStr.isBlank()) return 0;
        try {
            String numericString = hoursStr.replaceAll("[^\\d]", "");
            if (numericString.isEmpty()) return 0;
            return Integer.parseInt(numericString);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}