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
@Document(collection = "syllabi")
public class Syllabus {
    @Id
    private String id;
    private DisciplineInfo disciplineInfo;
    private String courseDescription; // Может содержать <br/> для HTML
    private List<String> lectureTopics;
    private List<String> practicalTopics;
    private List<String> literatureList;
    private AssessmentCriteria assessmentCriteria;
    private String generatedRawText;
}