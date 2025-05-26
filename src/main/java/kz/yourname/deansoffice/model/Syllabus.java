package kz.yourname.deansoffice.model; // Замените на ваш пакет

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;
import java.util.ArrayList; // Для инициализации списков

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "syllabi")
public class Syllabus {
    @Id
    private String id;

    private DisciplineInfo disciplineInfo;
    private String courseCode;

    private String courseDescription;
    private List<String> learningOutcomes = new ArrayList<>();
    private List<Map<String, String>> keyRoIndicators = new ArrayList<>();
    private List<Map<String, String>> detailedRoIndicators = new ArrayList<>();

    private String higherSchoolName;
    private String lectorNameAndPosition;
    private String lectorEmailAndPhone;
    private String lectorZoomId;
    private String assistantNameAndPosition;
    private String assistantEmailAndPhone;

    private String lecturesHours;
    private String seminarsHours;
    private String srspHours;
    private String srsHours;
    private String totalHours;
    private String finalControlType;

    private String prerequisites;
    private String postrequisites;

    private List<ThematicPlanModule> thematicPlan = new ArrayList<>();
    private List<String> literatureList = new ArrayList<>();
    private List<String> internetResources = new ArrayList<>();
    private List<String> softwareUsed = new ArrayList<>();
    private String disciplinePolicyText;
    private AssessmentCriteria assessmentCriteria; // Предполагается, что этот класс тоже есть
    private List<String> examinationQuestions = new ArrayList<>();
    private String generatedRawText;
}