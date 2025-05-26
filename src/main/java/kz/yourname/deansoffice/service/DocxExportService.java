package kz.yourname.deansoffice.service; // Замените kz.yourname.deansoffice на ваш пакет

import kz.yourname.deansoffice.model.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocxExportService {

    private static final String SYLLABUS_TEMPLATE_PATH = "templates/docx_templates/syllabus_template.docx";
    // private static final int PAGE_WIDTH_TWIPS = 8500; // Не используется активно

    public byte[] createSyllabusDocxFromTemplate(Syllabus syllabus) throws IOException {
        try (InputStream templateInputStream = new ClassPathResource(SYLLABUS_TEMPLATE_PATH).getInputStream();
             XWPFDocument document = new XWPFDocument(templateInputStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Map<String, String> calculatedPlaceholders = new HashMap<>();
            int totalTopics = 0;
            String topicRange = "0";

            if (syllabus.getThematicPlan() != null) {
                for (ThematicPlanModule module : syllabus.getThematicPlan()) {
                    if (module.getTopics() != null) {
                        totalTopics += module.getTopics().size();
                    }
                }
                if (totalTopics > 0) {
                    topicRange = "1-" + totalTopics;
                }

                int totalLectures = 0; int totalPractices = 0; int totalSrspSrs = 0;
                for (ThematicPlanModule module : syllabus.getThematicPlan()) {
                    if (module.getTopics() != null) {
                        for (ThematicPlanTopic topic : module.getTopics()) {
                            totalLectures += parseHours(topic.getLectureHours());
                            totalPractices += parseHours(topic.getSeminarHours());
                            totalSrspSrs += parseHours(topic.getSrspAndSrsHours());
                        }
                    }
                }
                calculatedPlaceholders.put("{{TOTAL_LECTURE_HOURS_THEMATIC_PLAN}}", String.valueOf(totalLectures));
                calculatedPlaceholders.put("{{TOTAL_PRACTICAL_HOURS_THEMATIC_PLAN}}", String.valueOf(totalPractices));
                calculatedPlaceholders.put("{{TOTAL_SRSP_SRS_HOURS_THEMATIC_PLAN}}", String.valueOf(totalSrspSrs));
                calculatedPlaceholders.put("{{TOTAL_HOURS_THEMATIC_PLAN}}", String.valueOf(totalLectures + totalPractices + totalSrspSrs));
            }
            calculatedPlaceholders.put("{{THEMATIC_PLAN_TOPIC_RANGE}}", topicRange);
            calculatedPlaceholders.put("{{THEMATIC_PLAN_TOTAL_TOPICS_COUNT}}", String.valueOf(totalTopics));

            replaceGeneralPlaceholders(document, syllabus, calculatedPlaceholders);

            XWPFTable thematicPlanTable = findTableByUniqueText(document, "№ недели");
            if (thematicPlanTable == null) {
                thematicPlanTable = findTableByUniqueText(document, "№ п/п");
            }

            if (thematicPlanTable != null && syllabus.getThematicPlan() != null && !syllabus.getThematicPlan().isEmpty()) {
                populateThematicPlanTableProgrammatically(thematicPlanTable, syllabus.getThematicPlan()); // Используем программное создание строк
            } else if (thematicPlanTable == null) {
                System.err.println("Таблица тематического плана не найдена в шаблоне по маркерам '№ недели' или '№ п/п'.");
            }

            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            System.err.println("Ошибка при загрузке или обработке шаблона DOCX: " + SYLLABUS_TEMPLATE_PATH + " | " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Непредвиденная ошибка при создании DOCX силлабуса из шаблона: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Непредвиденная ошибка при создании DOCX силлабуса", e);
        }
    }

    public byte[] createExamTicketsDocx(List<ExamTicket> tickets, String disciplineName) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            addMainTitle(document, "Экзаменационные билеты по дисциплине: " + (disciplineName != null ? disciplineName : "[Дисциплина]"), 16);
            addEmptyLine(document, 1);

            for (ExamTicket ticket : tickets) {
                addSectionTitle(document, "Билет №" + ticket.getTicketNumber(), 14, true, ParagraphAlignment.LEFT, 100, 50);

                addParagraphWithBoldPrefix(document, "Теоретические вопросы:");
                addListOfItemsAsSimpleText(document, ticket.getTheoreticalQuestions(), "  ");
                addEmptyLine(document, 1);

                addParagraphWithBoldPrefix(document, "Практические задачи:");
                addListOfItemsAsSimpleText(document, ticket.getPracticalTasks(), "  ");

                if (tickets.indexOf(ticket) < tickets.size() - 1) {
                    document.createParagraph().setPageBreak(true);
                } else {
                    addEmptyLine(document, 1);
                }
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    private void replaceGeneralPlaceholders(XWPFDocument document, Syllabus syllabus, Map<String, String> additionalPlaceholders) {
        replaceTextInParagraphs(document.getParagraphs(), syllabus, null, null, additionalPlaceholders);

        XWPFTable thematicPlanTableToSkip = findTableByUniqueText(document, "№ недели");
        if (thematicPlanTableToSkip == null) {
            thematicPlanTableToSkip = findTableByUniqueText(document, "№ п/п");
        }

        for (XWPFTable tbl : document.getTables()) {
            if (thematicPlanTableToSkip != null && tbl == thematicPlanTableToSkip) { // Исправлено сравнение
                if (!tbl.getRows().isEmpty()) {
                    XWPFTableRow lastRow = tbl.getRows().get(tbl.getRows().size() - 1);
                    if (isTotalsRow(lastRow)) {
                        for (XWPFTableCell cell : lastRow.getTableCells()) {
                            replaceTextInParagraphs(cell.getParagraphs(), syllabus, null, null, additionalPlaceholders);
                        }
                    }
                }
                continue;
            }

            for (XWPFTableRow row : tbl.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    replaceTextInParagraphs(cell.getParagraphs(), syllabus, null, null, additionalPlaceholders);
                }
            }
        }
    }

    private boolean isTotalsRow(XWPFTableRow row) {
        // Проверяем вторую ячейку (индекс 1) на текст "Итого:"
        if (row != null && row.getTableCells() != null && row.getTableCells().size() > 1 &&
                row.getCell(1) != null &&
                row.getCell(1).getText() != null &&
                row.getCell(1).getText().trim().equalsIgnoreCase("Итого:")) {
            return true;
        }
        return false;
    }

    private void replaceTextInParagraphs(List<XWPFParagraph> paragraphs, Syllabus syllabus, ThematicPlanTopic topic, String topicNumberInModule, Map<String, String> customPlaceholders) {
        if (paragraphs == null) return;

        for (XWPFParagraph p : paragraphs) {
            List<XWPFRun> runs = p.getRuns();
            if (runs.isEmpty()) continue;

            String paragraphFullText = p.getText();
            if (paragraphFullText == null || !paragraphFullText.contains("{{")) continue;
            String originalFullText = paragraphFullText;

            if (customPlaceholders != null) {
                for (Map.Entry<String, String> entry : customPlaceholders.entrySet()) {
                    if (paragraphFullText.contains(entry.getKey())) {
                        paragraphFullText = paragraphFullText.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
                    }
                }
            }
            if (syllabus != null && topic == null) { // Только общие плейсхолдеры syllabus
                DisciplineInfo di = syllabus.getDisciplineInfo() != null ? syllabus.getDisciplineInfo() : new DisciplineInfo();
                String courseCode = syllabus.getCourseCode() != null ? syllabus.getCourseCode() : "";
                String disciplineName = di.getDisciplineName() != null ? di.getDisciplineName() : "";
                if (paragraphFullText.contains("{{DISCIPLINE_NAME}}")) paragraphFullText = paragraphFullText.replace("{{DISCIPLINE_NAME}}", disciplineName);
                if (paragraphFullText.contains("{{COURSE_CODE}}")) paragraphFullText = paragraphFullText.replace("{{COURSE_CODE}}", courseCode);
                if (paragraphFullText.contains("{{COURSE_DESCRIPTION_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{COURSE_DESCRIPTION_TEXT}}", formatDocxText(syllabus.getCourseDescription()));
                if (paragraphFullText.contains("{{LEARNING_OUTCOMES_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{LEARNING_OUTCOMES_TEXT}}", formatListAsText(syllabus.getLearningOutcomes(), ""));
                if (paragraphFullText.contains("{{KEY_RO_INDICATORS_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{KEY_RO_INDICATORS_TEXT}}", formatRoIndicatorsForDocx(syllabus.getKeyRoIndicators()));
                if (paragraphFullText.contains("{{DETAILED_RO_INDICATORS_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{DETAILED_RO_INDICATORS_TEXT}}", formatRoIndicatorsForDocx(syllabus.getDetailedRoIndicators()));
                if (paragraphFullText.contains("{{PREREQUISITES_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{PREREQUISITES_TEXT}}", formatDocxText(syllabus.getPrerequisites()));
                if (paragraphFullText.contains("{{POSTREQUISITES_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{POSTREQUISITES_TEXT}}", formatDocxText(syllabus.getPostrequisites()));
                if (paragraphFullText.contains("{{LITERATURE_LIST_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{LITERATURE_LIST_TEXT}}", formatListAsText(syllabus.getLiteratureList(), "- "));
                if (paragraphFullText.contains("{{INTERNET_RESOURCES_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{INTERNET_RESOURCES_TEXT}}", formatListAsText(syllabus.getInternetResources(), "- "));
                if (paragraphFullText.contains("{{SOFTWARE_LIST_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{SOFTWARE_LIST_TEXT}}", formatListAsText(syllabus.getSoftwareUsed(), "- "));
                if (paragraphFullText.contains("{{EXAM_QUESTIONS_LIST_TEXT}}")) paragraphFullText = paragraphFullText.replace("{{EXAM_QUESTIONS_LIST_TEXT}}", formatListAsText(syllabus.getExaminationQuestions(), "1. "));
                if (paragraphFullText.contains("{{HIGHER_SCHOOL_NAME}}")) paragraphFullText = paragraphFullText.replace("{{HIGHER_SCHOOL_NAME}}", syllabus.getHigherSchoolName() != null ? syllabus.getHigherSchoolName() : "");
                if (paragraphFullText.contains("{{LECTOR_NAME_POSITION}}")) paragraphFullText = paragraphFullText.replace("{{LECTOR_NAME_POSITION}}", syllabus.getLectorNameAndPosition() != null ? syllabus.getLectorNameAndPosition() : "");
                if (paragraphFullText.contains("{{LECTOR_EMAIL_PHONE}}")) paragraphFullText = paragraphFullText.replace("{{LECTOR_EMAIL_PHONE}}", syllabus.getLectorEmailAndPhone() != null ? syllabus.getLectorEmailAndPhone() : "");
                if (paragraphFullText.contains("{{LECTURE_HOURS_VALUE}}")) paragraphFullText = paragraphFullText.replace("{{LECTURE_HOURS_VALUE}}", syllabus.getLecturesHours() != null ? syllabus.getLecturesHours() : "");
                if (paragraphFullText.contains("{{SEMINAR_HOURS_VALUE}}")) paragraphFullText = paragraphFullText.replace("{{SEMINAR_HOURS_VALUE}}", syllabus.getSeminarsHours() != null ? syllabus.getSeminarsHours() : "");
                if (paragraphFullText.contains("{{SRSP_HOURS_VALUE}}")) paragraphFullText = paragraphFullText.replace("{{SRSP_HOURS_VALUE}}", syllabus.getSrspHours() != null ? syllabus.getSrspHours() : "");
                if (paragraphFullText.contains("{{SRS_HOURS_VALUE}}")) paragraphFullText = paragraphFullText.replace("{{SRS_HOURS_VALUE}}", syllabus.getSrsHours() != null ? syllabus.getSrsHours() : "");
                if (paragraphFullText.contains("{{TOTAL_HOURS_VALUE}}")) paragraphFullText = paragraphFullText.replace("{{TOTAL_HOURS_VALUE}}", syllabus.getTotalHours() != null ? syllabus.getTotalHours() : "");
                if (paragraphFullText.contains("{{FINAL_CONTROL_TYPE_VALUE}}")) paragraphFullText = paragraphFullText.replace("{{FINAL_CONTROL_TYPE_VALUE}}", syllabus.getFinalControlType() != null ? syllabus.getFinalControlType() : "");
            }
            // Этот блок НЕ НУЖЕН, если populateThematicPlanTableProgrammatically создает строки тем программно
            /* if (topic != null) { // Плейсхолдеры для строки тематического плана
                if (paragraphFullText.contains("{{T_ROW_NUMBER}}")) paragraphFullText = paragraphFullText.replace("{{T_ROW_NUMBER}}", topicNumberInModule != null ? topicNumberInModule : (topic.getTopicNumberInModule() != null ? topic.getTopicNumberInModule() : ""));
                // ... и другие плейсхолдеры {{T_ROW_...}}
            }
            */

            if (!originalFullText.equals(paragraphFullText)) {
                for (int i = runs.size() - 1; i >= 0; i--) { p.removeRun(i); }
                XWPFRun newRun = p.createRun();
                if (paragraphFullText.contains("\n")) {
                    String[] lines = paragraphFullText.split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        newRun.setText(lines[i]);
                        if (i < lines.length - 1) { newRun.addBreak(); }
                    }
                } else { newRun.setText(paragraphFullText); }
            }
        }
    }

    private String formatDocxText(String text) {
        if (text == null) return "";
        return text.replace("<br/>", "\n").replace("<br />", "\n").replace("<br>", "\n");
    }

    private String formatListAsText(List<String> list, String prefix) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            if (item == null) continue;
            String formattedItem = formatDocxText(item);
            if (formattedItem.contains("\n")) {
                String[] lines = formattedItem.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    sb.append(i == 0 ? prefix : (prefix != null && !prefix.isEmpty() ? " ".repeat(prefix.length()) : "")).append(lines[i].trim()).append("\n");
                }
            } else {
                sb.append(prefix).append(formattedItem.trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String formatRoIndicatorsForDocx(List<Map<String, String>> indicators) {
        if (indicators == null || indicators.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> pair : indicators) {
            sb.append(formatDocxText(pair.getOrDefault("code", ""))).append(": ").append(formatDocxText(pair.getOrDefault("indicator", ""))).append("\n");
        }
        return sb.toString().trim();
    }

    private XWPFTable findTableByUniqueText(XWPFDocument document, String uniqueTextInHeaderCell) {
        for (XWPFTable table : document.getTables()) {
            if (table.getRows() != null && !table.getRows().isEmpty()) {
                XWPFTableRow firstRow = table.getRow(0);
                if (firstRow != null && firstRow.getTableCells() != null && !firstRow.getTableCells().isEmpty()) {
                    for (XWPFTableCell cell : firstRow.getTableCells()) {
                        if (cell != null && cell.getText() != null && cell.getText().trim().contains(uniqueTextInHeaderCell)) {
                            return table;
                        }
                    }
                }
                if (table.getRows().size() > 1) {
                    XWPFTableRow secondRow = table.getRow(1);
                    if (secondRow != null && secondRow.getTableCells() != null && !secondRow.getTableCells().isEmpty()) {
                        for (XWPFTableCell cell : secondRow.getTableCells()) {
                            if (cell != null && cell.getText() != null && cell.getText().trim().contains(uniqueTextInHeaderCell)) {
                                return table;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // ПРОГРАММНОЕ СОЗДАНИЕ СТРОК ТЕМ
    private void populateThematicPlanTableProgrammatically(XWPFTable table, List<ThematicPlanModule> thematicPlan) {
        if (table == null) {
            System.err.println("Таблица тематического плана не передана (null) в populateThematicPlanTableProgrammatically.");
            return;
        }
        if (thematicPlan == null || thematicPlan.isEmpty() || (thematicPlan.size() == 1 && thematicPlan.get(0).getModuleNumberAndName().contains("не удалось загрузить"))) {
            System.err.println("Тематический план пуст или содержит ошибку, строки тем не будут добавлены.");
            // Очищаем все строки после заголовков, кроме строки "Итого", если она есть
            int headerRowCount = 2; // Предполагаем 2 строки заголовка
            int totalRowsInTemplate = table.getRows().size();
            if (totalRowsInTemplate > headerRowCount) { // Если есть что удалять
                for (int i = totalRowsInTemplate - 2; i >= headerRowCount; i--) { // -2 потому что последняя строка - "Итого"
                    table.removeRow(i);
                }
            }
            return;
        }

        int headerRowCount = 2; // ВАШЕ КОЛИЧЕСТВО СТРОК ЗАГОЛОВКА
        int numberOfColumns = 6; // №, Название, Лек(ч), Прак(ч), СРСП/С(ч), РО

        // Удаляем все строки ПОСЛЕ строк заголовков, КРОМЕ последней строки "Итого"
        int totalInitialRows = table.getRows().size();
        int totalsRowIndex = totalInitialRows -1; // Индекс последней строки (предполагаем, это "Итого")

        if (totalInitialRows <= headerRowCount) {
            System.err.println("В шаблоне таблицы тематического плана слишком мало строк.");
            return;
        }

        // Удаляем все строки между заголовками и строкой "Итого"
        for (int i = totalsRowIndex - 1; i >= headerRowCount; i--) {
            table.removeRow(i);
        }

        int topicGlobalNumber = 0;
        int insertBeforeTotalsRowIndex = headerRowCount; // Начинаем вставлять после заголовков (на место удаленных)

        for (ThematicPlanModule module : thematicPlan) {
            XWPFTableRow moduleTitleRow = table.insertNewTableRow(insertBeforeTotalsRowIndex++);
            for(int c = 0; c < numberOfColumns; c++) { if(moduleTitleRow.getCell(c)==null) moduleTitleRow.createCell(); }

            XWPFTableCell moduleCell = moduleTitleRow.getCell(0);
            setCellTextWithStyle(moduleCell, module.getModuleNumberAndName() != null ? module.getModuleNumberAndName() : "[Модуль]", ParagraphAlignment.LEFT, true, 10, false);
            if (numberOfColumns > 1) {
                mergeCellsHorizontal(table, table.getRows().indexOf(moduleTitleRow), 0, numberOfColumns - 1);
            }

            if (module.getTopics() != null) {
                int topicCounterInModule = 0;
                for (ThematicPlanTopic topic : module.getTopics()) {
                    topicCounterInModule++;
                    topicGlobalNumber++;

                    XWPFTableRow newRow = table.insertNewTableRow(insertBeforeTotalsRowIndex++);
                    for(int c = 0; c < numberOfColumns; c++) { if(newRow.getCell(c)==null) newRow.createCell(); }

                    setCellTextWithStyle(newRow.getCell(0), String.valueOf(topicCounterInModule), ParagraphAlignment.CENTER, false, 10, true);

                    XWPFTableCell contentCell = newRow.getCell(1);
                    // Очищаем ячейку перед добавлением нового контента
                    for (int pIdx = contentCell.getParagraphs().size() - 1; pIdx >= 0; pIdx--) contentCell.removeParagraph(pIdx);

                    if (topic.getGeneralTopicTitle() != null && !topic.getGeneralTopicTitle().isBlank()) addStyledTextToCell(contentCell, String.valueOf(topicCounterInModule) + ". ", topic.getGeneralTopicTitle(), true, true); // clearCellFirst = true для первого
                    if (topic.getLectureTheme() != null && !topic.getLectureTheme().isBlank()) addStyledTextToCell(contentCell, "Тема лекции: ", topic.getLectureTheme(), true, false);
                    if (topic.getLectureContent() != null && !topic.getLectureContent().isEmpty()) addStyledTextToCell(contentCell, "  ", String.join("\n", topic.getLectureContent()), false, false);
                    if (topic.getPracticalTheme() != null && !topic.getPracticalTheme().isBlank()) addStyledTextToCell(contentCell, "Тема практического занятия: ", topic.getPracticalTheme(), true, false);
                    if (topic.getPracticalContent() != null && !topic.getPracticalContent().isEmpty()) addStyledTextToCell(contentCell, "  ", String.join("\n", topic.getPracticalContent()), false, false);
                    if (topic.getTasks() != null && !topic.getTasks().isEmpty()) addStyledTextToCell(contentCell, "Задания: ", String.join("\n- ", topic.getTasks()), true, false);
                    if (topic.getSrspTheme() != null && !topic.getSrspTheme().isBlank()) addStyledTextToCell(contentCell, "Тема СРСП: ", topic.getSrspTheme(), true, false);
                    if (topic.getSrspTasksList() != null && !topic.getSrspTasksList().isEmpty()) addStyledTextToCell(contentCell, "Задания СРСП: ", String.join("\n- ", topic.getSrspTasksList()), true, false);
                    contentCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);

                    setCellTextWithStyle(newRow.getCell(2), topic.getLectureHours(), ParagraphAlignment.CENTER, false, 10, true);
                    setCellTextWithStyle(newRow.getCell(3), topic.getSeminarHours(), ParagraphAlignment.CENTER, false, 10, true);
                    setCellTextWithStyle(newRow.getCell(4), topic.getSrspAndSrsHours(), ParagraphAlignment.CENTER, false, 10, true);
                    setCellTextWithStyle(newRow.getCell(5), (topic.getRoCovered() != null && !topic.getRoCovered().isEmpty()) ? String.join(", ", topic.getRoCovered()) : "", ParagraphAlignment.CENTER, false, 10, true);
                }
            }
        }
    }

    private void calculateAndReplaceTotalHoursPlaceholders(XWPFDocument document, List<ThematicPlanModule> thematicPlan) {
        // ... (код без изменений)
        if (thematicPlan == null) return;
        int totalLectures = 0; int totalPractices = 0; int totalSrspSrs = 0;
        for (ThematicPlanModule module : thematicPlan) {
            if (module.getTopics() != null) {
                for (ThematicPlanTopic topic : module.getTopics()) {
                    totalLectures += parseHours(topic.getLectureHours());
                    totalPractices += parseHours(topic.getSeminarHours());
                    totalSrspSrs += parseHours(topic.getSrspAndSrsHours());
                }
            }
        }
        int grandTotal = totalLectures + totalPractices + totalSrspSrs;
        Map<String, String> totalHoursPlaceholders = Map.of(
                "{{TOTAL_LECTURE_HOURS_THEMATIC_PLAN}}", String.valueOf(totalLectures),
                "{{TOTAL_PRACTICAL_HOURS_THEMATIC_PLAN}}", String.valueOf(totalPractices),
                "{{TOTAL_SRSP_SRS_HOURS_THEMATIC_PLAN}}", String.valueOf(totalSrspSrs),
                "{{TOTAL_HOURS_THEMATIC_PLAN}}", String.valueOf(grandTotal)
        );
        replaceTextInParagraphs(document.getParagraphs(), totalHoursPlaceholders);
        for (XWPFTable tbl : document.getTables()) {
            for (XWPFTableRow row : tbl.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    replaceTextInParagraphs(cell.getParagraphs(), totalHoursPlaceholders);
                }
            }
        }
    }

    private int parseHours(String hoursStr) {
        // ... (код без изменений)
        if (hoursStr == null || hoursStr.isBlank()) return 0;
        try {
            String numericString = hoursStr.replaceAll("[^\\d]", "");
            if (numericString.isEmpty()) return 0;
            return Integer.parseInt(numericString);
        } catch (NumberFormatException e) {
            System.err.println("Не удалось распознать часы: " + hoursStr + " | " + e.getMessage());
            return 0;
        }
    }

    private void replaceTextInParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> customReplacements) {
        replaceTextInParagraphs(paragraphs, null, null, null, customReplacements);
    }

    private void addMainTitle(XWPFDocument document, String text, int fontSize) {
        // ... (код без изменений)
        XWPFParagraph titleP = document.createParagraph();
        titleP.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleR = titleP.createRun();
        titleR.setBold(true); titleR.setFontSize(fontSize); titleR.setText(text);
        titleP.setSpacingAfter(200);
    }

    private void addSectionTitle(XWPFDocument document, String titleText, int fontSize, boolean isBold, ParagraphAlignment alignment, int spacingBefore, int spacingAfter) {
        // ... (код без изменений)
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        if (spacingBefore > 0) paragraph.setSpacingBefore(spacingBefore);
        if (spacingAfter > 0) paragraph.setSpacingAfter(spacingAfter);
        XWPFRun run = paragraph.createRun();
        run.setBold(isBold); run.setFontSize(fontSize); run.setText(titleText);
    }

    private void addEmptyLine(XWPFDocument document, int count) {
        // ... (код без изменений)
        for (int i = 0; i < count; i++) {
            XWPFParagraph p = document.createParagraph();
            p.setSpacingAfter(120);
        }
    }

    private void addParagraphWithBoldPrefix(XWPFDocument document, String prefix) {
        // ... (код без изменений)
        XWPFParagraph p = document.createParagraph();
        XWPFRun rPrefix = p.createRun();
        rPrefix.setBold(true); rPrefix.setFontSize(11); rPrefix.setText(prefix);
        p.setSpacingAfter(60);
    }

    private void addListOfItemsAsSimpleText(XWPFDocument document, List<String> items, String itemPrefix) {
        // ... (код без изменений)
        if (items == null || items.isEmpty()) {
            addFormattedText(document, itemPrefix + "[Список пуст или нет данных]");
            return;
        }
        for (String item : items) {
            if (item == null || item.isBlank()) continue;
            String cleanItem = item.replace("<br/>", "\n").replace("<br />", "\n").replace("<br>", "\n");
            addFormattedText(document, itemPrefix + cleanItem);
        }
    }

    private void addFormattedText(XWPFDocument document, String textWithNewlines) {
        // ... (код без изменений)
        if (textWithNewlines == null || textWithNewlines.isBlank()) {
            XWPFParagraph p = document.createParagraph();
            p.createRun().setText("[Нет данных]");
            p.setSpacingAfter(60);
            return;
        }
        String[] lines = textWithNewlines.split("\\n");
        for (String line : lines) {
            XWPFParagraph p = document.createParagraph();
            p.createRun().setText(line.trim());
            p.setSpacingAfter(60);
        }
    }

    private XWPFParagraph getOrCreateParagraph(XWPFTableCell cell) {
        // ... (код без изменений)
        if (cell.getParagraphs() != null && !cell.getParagraphs().isEmpty()) {
            XWPFParagraph p = cell.getParagraphs().get(0);
            for (int i = p.getRuns().size() - 1; i >= 0; i--) { p.removeRun(i); }
            for (int i = cell.getParagraphs().size() - 1; i > 0; i--) { cell.removeParagraph(i); }
            return p;
        }
        return cell.addParagraph();
    }

    private void setCellTextWithStyle(XWPFTableCell cell, String text, ParagraphAlignment alignment, boolean isBold, int fontSize, boolean verticalCenter) {
        // ... (код без изменений)
        if (cell == null) return;
        XWPFParagraph p = getOrCreateParagraph(cell);
        p.setAlignment(alignment);

        if (verticalCenter) {
            CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
            if(tcPr.getVAlign() == null) tcPr.addNewVAlign();
            tcPr.getVAlign().setVal(STVerticalJc.CENTER);
        }

        if (text != null && text.contains("\n")) {
            String[] lines = text.split("\\n");
            XWPFRun run = p.createRun();
            run.setBold(isBold);
            run.setFontSize(fontSize);
            for (int k = 0; k < lines.length; k++) {
                run.setText(lines[k]);
                if (k < lines.length - 1) {
                    run.addBreak();
                }
            }
        } else if (text != null) {
            XWPFRun run = p.createRun();
            run.setBold(isBold);
            run.setFontSize(fontSize);
            run.setText(text);
        } else {
            XWPFRun run = p.createRun();
            run.setFontSize(fontSize);
            run.setText("");
        }
    }

    private void setColumnWidth(XWPFTableCell cell, int width) {
        // ... (код без изменений)
        if (cell == null) return;
        if (cell.getCTTc().getTcPr() == null) cell.getCTTc().addNewTcPr();
        CTTblWidth tblWidth = cell.getCTTc().getTcPr().isSetTcW() ? cell.getCTTc().getTcPr().getTcW() : cell.getCTTc().getTcPr().addNewTcW();
        tblWidth.setW(BigInteger.valueOf(width));
        tblWidth.setType(STTblWidth.DXA);
    }

    private void addStyledTextToCell(XWPFTableCell cell, String prefix, String text, boolean isPrefixBold, boolean clearCellFirst) {
        // ... (код без изменений)
        if (text == null || text.isBlank()) {
            if (clearCellFirst && (cell.getParagraphs() == null || cell.getParagraphs().isEmpty())) {
                cell.addParagraph();
            } else if (clearCellFirst) {
                getOrCreateParagraph(cell);
            }
            return;
        }

        XWPFParagraph p;
        if(clearCellFirst){
            p = getOrCreateParagraph(cell);
        } else {
            p = cell.addParagraph();
        }

        if (prefix != null && !prefix.isBlank()) {
            XWPFRun rPrefix = p.createRun();
            rPrefix.setBold(isPrefixBold); rPrefix.setFontSize(10); rPrefix.setText(prefix);
        }

        String textToAdd = text.trim();
        if (prefix != null && !prefix.isBlank() && !textToAdd.startsWith(" ") && !textToAdd.isEmpty()) {
            textToAdd = " " + textToAdd;
        }

        if (textToAdd.contains("\n")) {
            String[] lines = textToAdd.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                XWPFRun rText = p.createRun();
                rText.setFontSize(10);
                rText.setText(lines[i].trim());
                if (i < lines.length - 1) {
                    rText.addBreak();
                }
            }
        } else {
            XWPFRun rText = p.createRun();
            rText.setFontSize(10);
            rText.setText(textToAdd);
        }
        p.setSpacingAfter(0);
        p.setSpacingBefore(0);
    }

    private void mergeCellsHorizontal(XWPFTable table, int rowIndex, int fromCell, int toCell) {
        // ... (код без изменений)
        if (rowIndex < 0 || rowIndex >= table.getRows().size() || table.getRow(rowIndex) == null ||
                fromCell < 0 || toCell < fromCell || table.getRow(rowIndex).getTableCells().size() <= toCell) {
            System.err.println("Некорректные параметры для mergeCellsHorizontal: rowIndex=" + rowIndex + " from=" + fromCell + " to=" + toCell);
            return;
        }
        XWPFTableCell firstCell = table.getRow(rowIndex).getCell(fromCell);
        if (firstCell == null) return;

        CTTcPr tcPrFirst = firstCell.getCTTc().isSetTcPr() ? firstCell.getCTTc().getTcPr() : firstCell.getCTTc().addNewTcPr();
        if (tcPrFirst.getHMerge() == null) tcPrFirst.addNewHMerge();
        tcPrFirst.getHMerge().setVal(STMerge.RESTART);

        for (int i = fromCell + 1; i <= toCell; i++) {
            XWPFTableCell mergedCell = table.getRow(rowIndex).getCell(i);
            if (mergedCell == null) continue;
            CTTcPr tcPrMerged = mergedCell.getCTTc().isSetTcPr() ? mergedCell.getCTTc().getTcPr() : mergedCell.getCTTc().addNewTcPr();
            if (tcPrMerged.getHMerge() == null) tcPrMerged.addNewHMerge();
            tcPrMerged.getHMerge().setVal(STMerge.CONTINUE);
        }
    }

    private void mergeCellsVertical(XWPFTable table, int colIndex, int fromRow, int toRow) {
        // ... (код без изменений)
        if (fromRow < 0 || toRow < fromRow || colIndex < 0 ||
                table.getRows().size() <= fromRow || table.getRows().size() <= toRow ) {
            System.err.println("Некорректные индексы строк для mergeCellsVertical: col=" + colIndex + ", from=" + fromRow + ", to=" + toRow + ", totalRows=" + table.getRows().size());
            return;
        }

        for(int r = fromRow; r <= toRow; r++) {
            if (table.getRow(r) == null || table.getRow(r).getTableCells().size() <= colIndex || table.getRow(r).getCell(colIndex) == null) {
                System.err.println("Некорректный индекс колонки или отсутствующая ячейка для mergeCellsVertical: row=" + r + ", col=" + colIndex);
                return;
            }
        }

        XWPFTableCell firstCell = table.getRow(fromRow).getCell(colIndex);
        if (firstCell == null) return;

        CTTcPr tcPrFirst = firstCell.getCTTc().isSetTcPr() ? firstCell.getCTTc().getTcPr() : firstCell.getCTTc().addNewTcPr();
        if (tcPrFirst.getVMerge() == null) tcPrFirst.addNewVMerge();
        tcPrFirst.getVMerge().setVal(STMerge.RESTART);
        if(tcPrFirst.getVAlign() == null) tcPrFirst.addNewVAlign();
        tcPrFirst.getVAlign().setVal(STVerticalJc.CENTER);


        for (int i = fromRow + 1; i <= toRow; i++) {
            XWPFTableCell mergedCell = table.getRow(i).getCell(colIndex);
            if (mergedCell == null) continue;
            CTTcPr tcPrMerged = mergedCell.getCTTc().isSetTcPr() ? mergedCell.getCTTc().getTcPr() : mergedCell.getCTTc().addNewTcPr();
            if (tcPrMerged.getVMerge() == null) tcPrMerged.addNewVMerge();
            tcPrMerged.getVMerge().setVal(STMerge.CONTINUE);
            if(tcPrMerged.getVAlign() == null) tcPrMerged.addNewVAlign();
            tcPrMerged.getVAlign().setVal(STVerticalJc.CENTER);
        }
    }
}