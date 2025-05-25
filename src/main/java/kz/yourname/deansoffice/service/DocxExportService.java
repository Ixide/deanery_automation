package kz.yourname.deansoffice.service;

import kz.yourname.deansoffice.model.ExamTicket;
import kz.yourname.deansoffice.model.Syllabus;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class DocxExportService {

    public byte[] createSyllabusDocx(Syllabus syllabus) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            addMainTitle(document, syllabus.getDisciplineInfo() != null ? syllabus.getDisciplineInfo().getDisciplineName() : "Силлабус");

            if (syllabus.getDisciplineInfo() != null) {
                addSectionTitle(document, "Общая информация");
                addParagraph(document, "Дисциплина: " + syllabus.getDisciplineInfo().getDisciplineName());
                addParagraph(document, "Специальность: " + syllabus.getDisciplineInfo().getSpecialty());
                addParagraph(document, "Образовательные цели: " + syllabus.getDisciplineInfo().getEducationalGoals());
                addEmptyLine(document);
            }

            addSectionTitle(document, "Описание курса");
            addFormattedParagraphs(document, syllabus.getCourseDescription());
            addEmptyLine(document);

            addSectionTitle(document, "Темы лекций");
            addListOfItems(document, syllabus.getLectureTopics());
            addEmptyLine(document);

            addSectionTitle(document, "Темы практических занятий");
            addListOfItems(document, syllabus.getPracticalTopics());
            addEmptyLine(document);

            addSectionTitle(document, "Список рекомендуемой литературы");
            addListOfItems(document, syllabus.getLiteratureList());
            addEmptyLine(document);

            if (syllabus.getAssessmentCriteria() != null) {
                addSectionTitle(document, "Критерии оценки");
                addParagraph(document, "Тип системы: " + syllabus.getAssessmentCriteria().getGradingSystemType());
                if (syllabus.getAssessmentCriteria().getCriteriaDetails() != null) {
                    for (Map.Entry<String, String> entry : syllabus.getAssessmentCriteria().getCriteriaDetails().entrySet()) {
                        addParagraph(document, entry.getKey() + ": " + entry.getValue());
                    }
                }
                if (syllabus.getAssessmentCriteria().getDetailedBreakdown() != null) {
                    addParagraph(document, "Развернутое описание:");
                    addListOfItems(document, syllabus.getAssessmentCriteria().getDetailedBreakdown());
                }
                addEmptyLine(document);
            }

            document.write(out);
            return out.toByteArray();
        }
    }

    public byte[] createExamTicketsDocx(List<ExamTicket> tickets, String disciplineName) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            addMainTitle(document, "Экзаменационные билеты по дисциплине: " + disciplineName);
            addEmptyLine(document);

            for (ExamTicket ticket : tickets) {
                addSectionTitle(document, "Билет №" + ticket.getTicketNumber());

                addParagraphWithBoldPrefix(document, "Теоретические вопросы:");
                addListOfItems(document, ticket.getTheoreticalQuestions());
                addEmptyLine(document);

                addParagraphWithBoldPrefix(document, "Практические задачи:");
                addListOfItems(document, ticket.getPracticalTasks());

                if (tickets.indexOf(ticket) < tickets.size() - 1) {
                    document.createParagraph().setPageBreak(true);
                } else {
                    addEmptyLine(document);
                }
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    // Вспомогательные методы для DOCX
    private void addMainTitle(XWPFDocument document, String text) {
        XWPFParagraph titleP = document.createParagraph();
        titleP.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleR = titleP.createRun();
        titleR.setBold(true);
        titleR.setFontSize(16);
        titleR.setText(text);
    }

    private void addSectionTitle(XWPFDocument document, String text) {
        XWPFParagraph p = document.createParagraph();
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(14);
        r.setText(text);
    }

    private void addParagraph(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) return;
        XWPFParagraph p = document.createParagraph();
        p.createRun().setText(text);
    }

    private void addFormattedParagraphs(XWPFDocument document, String htmlFormattedText) {
        if (htmlFormattedText == null || htmlFormattedText.isBlank()) {
            addParagraph(document, "[Нет данных]");
            return;
        }
        String textWithPoiBreaks = htmlFormattedText.replace("<br/>", "\n").replace("<br />", "\n");
        String[] lines = textWithPoiBreaks.split("\\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) { // Добавляем непустые строки
                addParagraph(document, line.trim());
            } else { // Для пустых строк после <br> (двойной перенос)
                addEmptyLine(document);
            }
        }
    }


    private void addListOfItems(XWPFDocument document, List<String> items) {
        if (items == null || items.isEmpty()) {
            addParagraph(document, "[Нет данных]");
            return;
        }
        for (String item : items) {
            if (item == null || item.isBlank()) continue;
            // Для простоты каждая строка списка как новый абзац с маркером
            // или если в item уже есть <br/>, то addFormattedParagraphs
            if (item.contains("<br/>") || item.contains("<br />")) {
                addFormattedParagraphs(document, "- " + item);
            } else {
                addParagraph(document, "- " + item);
            }
        }
    }

    private void addParagraphWithBoldPrefix(XWPFDocument document, String prefix) {
        XWPFParagraph p = document.createParagraph();
        XWPFRun rPrefix = p.createRun();
        rPrefix.setBold(true);
        rPrefix.setText(prefix);
    }

    private void addEmptyLine(XWPFDocument document) {
        document.createParagraph();
    }
}