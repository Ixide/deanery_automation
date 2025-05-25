package kz.yourname.deansoffice.controller;

import kz.yourname.deansoffice.dto.ExamTicketRequest;
import kz.yourname.deansoffice.dto.SyllabusRequest;
import kz.yourname.deansoffice.model.ExamTicket;
import kz.yourname.deansoffice.model.Syllabus;
import kz.yourname.deansoffice.repository.SyllabusRepository;
import kz.yourname.deansoffice.service.DocxExportService;
import kz.yourname.deansoffice.service.DocumentGenerationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

@Controller
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentGenerationService documentGenerationService;
    private final DocxExportService docxExportService;
    private final SyllabusRepository syllabusRepository;


    public DocumentController(DocumentGenerationService documentGenerationService,
                              DocxExportService docxExportService,
                              SyllabusRepository syllabusRepository) {
        this.documentGenerationService = documentGenerationService;
        this.docxExportService = docxExportService;
        this.syllabusRepository = syllabusRepository;
    }

    @GetMapping("/")
    public String homePage(Model model) {
        model.addAttribute("pageTitle", "Главная - Автоматизация Деканата");
        return "index";
    }

    // --- Syllabus ---
    @GetMapping("/syllabus/new")
    public String showSyllabusForm(Model model) {
        model.addAttribute("syllabusRequest", new SyllabusRequest());
        model.addAttribute("pageTitle", "Генерация Силлабуса");
        return "syllabus-form";
    }

    @PostMapping("/syllabus/generate")
    public String generateSyllabus(@ModelAttribute SyllabusRequest syllabusRequest, Model model) {
        try {
            Syllabus syllabus = documentGenerationService.generateSyllabus(syllabusRequest);
            model.addAttribute("syllabus", syllabus);
            model.addAttribute("pageTitle", "Результат генерации силлабуса");
            return "syllabus-result";
        } catch (Exception e) {
            logger.error("Ошибка при генерации силлабуса: ", e);
            model.addAttribute("error", "Ошибка при генерации силлабуса: " + e.getMessage());
            model.addAttribute("syllabusRequest", syllabusRequest);
            model.addAttribute("pageTitle", "Ошибка генерации силлабуса");
            return "syllabus-form";
        }
    }

    @GetMapping("/syllabus/download/{syllabusId}")
    public ResponseEntity<byte[]> downloadSyllabusDocx(@PathVariable String syllabusId) {
        try {
            Syllabus syllabus = syllabusRepository.findById(syllabusId)
                    .orElseThrow(() -> new IllegalArgumentException("Силлабус с ID " + syllabusId + " не найден."));

            byte[] docxBytes = docxExportService.createSyllabusDocx(syllabus);

            String safeDisciplineName = syllabus.getDisciplineInfo() != null && syllabus.getDisciplineInfo().getDisciplineName() != null ?
                    syllabus.getDisciplineInfo().getDisciplineName().replaceAll("[^a-zA-Z0-9а-яА-ЯЁё_\\s-]", "").replace(" ", "_") :
                    "syllabus";
            String fileName = "syllabus_" + safeDisciplineName + ".docx";

            logger.info("Запрос на скачивание силлабуса: {}", fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docxBytes);

        } catch (IOException e) {
            logger.error("Ошибка генерации DOCX файла для силлабуса ID {}: ", syllabusId, e);
            return ResponseEntity.status(500).body(("Ошибка генерации DOCX файла: " + e.getMessage()).getBytes());
        } catch (IllegalArgumentException e) {
            logger.warn("Ошибка при запросе на скачивание силлабуса: {}", e.getMessage());
            return ResponseEntity.status(404).body(e.getMessage().getBytes());
        }
    }

    // --- Exam Tickets ---
    @GetMapping("/exam-tickets/new")
    public String showExamTicketForm(Model model) {
        model.addAttribute("examTicketRequest", new ExamTicketRequest());
        model.addAttribute("pageTitle", "Генерация Экзаменационных Билетов");
        return "exam-ticket-form";
    }

    @PostMapping("/exam-tickets/generate")
    public String generateExamTickets(@ModelAttribute ExamTicketRequest examTicketRequest, Model model, HttpSession session) {
        try {
            List<ExamTicket> tickets = documentGenerationService.generateExamTickets(examTicketRequest);
            model.addAttribute("tickets", tickets);
            model.addAttribute("disciplineName", examTicketRequest.getDisciplineName());
            model.addAttribute("pageTitle", "Результат генерации билетов");

            session.setAttribute("lastGeneratedTickets", tickets);
            session.setAttribute("lastGeneratedTicketsDiscipline", examTicketRequest.getDisciplineName());

            return "exam-ticket-result";
        } catch (Exception e) {
            logger.error("Ошибка при генерации экзаменационных билетов: ", e);
            model.addAttribute("error", "Ошибка при генерации экзаменационных билетов: " + e.getMessage());
            model.addAttribute("examTicketRequest", examTicketRequest);
            model.addAttribute("pageTitle", "Ошибка генерации билетов");
            return "exam-ticket-form";
        }
    }

    @GetMapping("/exam-tickets/download")
    public ResponseEntity<byte[]> downloadExamTicketsDocx(HttpSession session) {
        try {
            @SuppressWarnings("unchecked")
            List<ExamTicket> tickets = (List<ExamTicket>) session.getAttribute("lastGeneratedTickets");
            String disciplineName = (String) session.getAttribute("lastGeneratedTicketsDiscipline");

            if (tickets == null || tickets.isEmpty() || disciplineName == null) {
                logger.warn("Попытка скачать билеты без предварительной генерации или данные в сессии отсутствуют.");
                throw new IllegalArgumentException("Нет данных о билетах для скачивания. Пожалуйста, сгенерируйте их сначала.");
            }

            byte[] docxBytes = docxExportService.createExamTicketsDocx(tickets, disciplineName);

            String safeDisciplineName = disciplineName.replaceAll("[^a-zA-Z0-9а-яА-ЯЁё_\\s-]", "").replace(" ", "_");
            String fileName = "exam_tickets_" + safeDisciplineName + ".docx";

            logger.info("Запрос на скачивание экзаменационных билетов: {}", fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docxBytes);

        } catch (IOException e) {
            logger.error("Ошибка генерации DOCX файла для билетов: ", e);
            return ResponseEntity.status(500).body(("Ошибка генерации DOCX файла для билетов: " + e.getMessage()).getBytes());
        } catch (IllegalArgumentException e) {
            logger.warn("Ошибка при запросе на скачивание билетов: {}", e.getMessage());
            return ResponseEntity.status(400).body(e.getMessage().getBytes());
        }
    }
}