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
    private Map<String, String> criteriaDetails;
    private List<String> detailedBreakdown;
}