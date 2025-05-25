package kz.yourname.deansoffice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisciplineInfo {
    private String disciplineName;
    private String specialty;
    private String educationalGoals;
}