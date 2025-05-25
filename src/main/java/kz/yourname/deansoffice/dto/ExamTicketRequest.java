package kz.yourname.deansoffice.dto;

import lombok.Data;

@Data
public class ExamTicketRequest {
    private String disciplineName;
    private int numberOfTickets;
    private String topicsToCover;
}