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

            // Заголовок документа (название дисциплины)
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText("СИЛЛАБУС");
            titleRun.addBreak();
            titleRun.setText(syllabus.getDisciplineInfo().getDisciplineName().toUpperCase());
            titleRun.addBreak();

            // Информация о специальности и целях
            addSectionTitle(document, "1. Общая информация");
            addParagraphText(document, "Специальность: " + syllabus.getDisciplineInfo().getSpecialty());
            addParagraphText(document, "Образовательные цели: " + syllabus.getDisciplineInfo().getEducationalGoals());
            document.createParagraph().createRun().addBreak(); // Пустая строка для отступа

            // Описание курса
            addSectionTitle(document, "2. Описание курса");
            addFormattedText(document, syllabus.getCourseDescription());
            document.createParagraph().createRun().addBreak();

            // Темы лекций
            addSectionTitle(document, "3. Темы лекций");
            addListToDoc(document, syllabus.getLectureTopics());
            document.createParagraph().createRun().addBreak();

            // Темы практических занятий
            addSectionTitle(document, "4. Темы практических занятий");
            addListToDoc(document, syllabus.getPracticalTopics());
            document.createParagraph().createRun().addBreak();

            // Список литературы
            addSectionTitle(document, "5. Список рекомендуемой литературы");
            addListToDoc(document, syllabus.getLiteratureList());
            document.createParagraph().createRun().addBreak();

            // Критерии оценки
            addSectionTitle(document, "6. Критерии оценки");
            if (syllabus.getAssessmentCriteria() != null) {
                addParagraphText(document, "Тип системы: " + syllabus.getAssessmentCriteria().getGradingSystemType());
                if (syllabus.getAssessmentCriteria().getCriteriaDetails() != null) {
                    for (Map.Entry<String, String> entry : syllabus.getAssessmentCriteria().getCriteriaDetails().entrySet()) {
                        addParagraphText(document, entry.getKey() + ": " + entry.getValue());
                    }
                }
                if (syllabus.getAssessmentCriteria().getDetailedBreakdown() != null) {
                    addParagraphText(document, "Развернутое описание:");
                    for (String detail : syllabus.getAssessmentCriteria().getDetailedBreakdown()) {
                        addFormattedText(document, "- " + detail); // Используем addFormattedText для обработки переносов строк
                    }
                }
            } else {
                addParagraphText(document, "Нет данных.");
            }

            document.write(out);
            return out.toByteArray();
        }
    }

    public byte[] createExamTicketsDocx(List<ExamTicket> tickets, String disciplineName) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText("ЭКЗАМЕНАЦИОННЫЕ БИЛЕТЫ");
            titleRun.addBreak();
            titleRun.setText("по дисциплине: " + disciplineName.toUpperCase());
            titleRun.addBreak();
            titleRun.addBreak();


            for (ExamTicket ticket : tickets) {
                addSectionTitle(document, "Билет №" + ticket.getTicketNumber());

                XWPFParagraph theoreticalParagraph = document.createParagraph();
                XWPFRun theoreticalRun = theoreticalParagraph.createRun();
                theoreticalRun.setBold(true);
                theoreticalRun.setText("Теоретические вопросы:");

                if (ticket.getTheoreticalQuestions() != null && !ticket.getTheoreticalQuestions().isEmpty()) {
                    for (int i = 0; i < ticket.getTheoreticalQuestions().size(); i++) {
                        addParagraphText(document, (i + 1) + ". " + ticket.getTheoreticalQuestions().get(i));
                    }
                } else {
                    addParagraphText(document, "Нет данных.");
                }
                document.createParagraph().createRun().addBreak(); // Отступ

                XWPFParagraph practicalParagraph = document.createParagraph();
                XWPFRun practicalRun = practicalParagraph.createRun();
                practicalRun.setBold(true);
                practicalRun.setText("Практические задачи:");

                if (ticket.getPracticalTasks() != null && !ticket.getPracticalTasks().isEmpty()) {
                    for (int i = 0; i < ticket.getPracticalTasks().size(); i++) {
                        addParagraphText(document, (i + 1) + ". " + ticket.getPracticalTasks().get(i));
                    }
                } else {
                    addParagraphText(document, "Нет данных.");
                }

                // Добавляем разрыв страницы после каждого билета, кроме последнего
                if (tickets.indexOf(ticket) < tickets.size() - 1) {
                    document.createParagraph().setPageBreak(true);
                } else {
                    document.createParagraph().createRun().addBreak(); // Отступ после последнего билета
                }
            }

            document.write(out);
            return out.toByteArray();
        }
    }

    // Вспомогательные методы для форматирования
    private void addSectionTitle(XWPFDocument document, String titleText) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(14);
        run.setText(titleText);
    }

    private void addParagraphText(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) return;
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }

    private void addFormattedText(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) {
            addParagraphText(document, "Нет данных.");
            return;
        }
        String[] lines = text.split("\\n"); // Разбиваем текст на строки по символу новой строки
        for (String line : lines) {
            addParagraphText(document, line); // Каждую строку добавляем как отдельный абзац (или можно в один run с run.addBreak())
        }
    }


    private void addListToDoc(XWPFDocument document, List<String> items) {
        if (items == null || items.isEmpty()) {
            addParagraphText(document, "Нет данных.");
            return;
        }
        for (String item : items) {
            XWPFParagraph paragraph = document.createParagraph();
            // Для нумерованного или маркированного списка можно использовать Numbering
            // Но для простоты пока просто добавим текст с маркером
            paragraph.setNumID(null); // Сброс нумерации, если она была применена
            XWPFRun run = paragraph.createRun();
            // Если элементы списка уже содержат маркеры, можно их не добавлять
            if (item.matches("^\\d+\\.\\s+.*") || item.matches("^-\\s+.*") || item.matches("^\\*\\s+.*")) {
                run.setText(item);
            } else {
                run.setText("- " + item); // Добавляем дефис как маркер по умолчанию
            }
        }
    }
}