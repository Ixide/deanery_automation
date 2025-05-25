package kz.yourname.deansoffice.dto;

import lombok.Data;

@Data
public class SyllabusRequest {
    private String disciplineName;
    private String specialty;
    private String educationalGoals;
}