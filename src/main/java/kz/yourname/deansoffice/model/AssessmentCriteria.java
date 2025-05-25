package kz.yourname.deansoffice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentCriteria {
    private String gradingSystemType;
    private Map<String, String> criteriaDetails; // e.g., {"Exam": "50%", "Assignments": "30%"}
    private List<String> detailedBreakdown;
}