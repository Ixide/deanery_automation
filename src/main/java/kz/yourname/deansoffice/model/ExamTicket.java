package kz.yourname.deansoffice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exam_tickets")
public class ExamTicket {
    @Id
    private String id;
    private String disciplineName;
    private int ticketNumber;
    private List<String> theoreticalQuestions;
    private List<String> practicalTasks;
    private String generatedRawText;
}