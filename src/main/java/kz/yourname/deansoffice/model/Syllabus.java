package kz.yourname.deansoffice.model; // Замените на ваш пакет

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

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
    private List<String> learningOutcomes;

    // Поля для двух разных наборов индикаторов РО
    private List<Map<String, String>> keyRoIndicators;      // Для {{KEY_RO_INDICATORS_TEXT}}
    private List<Map<String, String>> detailedRoIndicators; // Для {{DETAILED_RO_INDICATORS_TEXT}}

    // Контактная информация (плейсхолдеры)
    private String higherSchoolName;
    private String lectorNameAndPosition;
    private String lectorEmailAndPhone;
    private String lectorZoomId;
    private String assistantNameAndPosition;
    private String assistantEmailAndPhone;

    // Данные для таблицы часов (плейсхолдеры)
    private String lecturesHours;
    private String seminarsHours;
    private String srspHours;
    private String srsHours;
    private String totalHours;
    private String finalControlType;

    // Пререквизиты и Постреквизиты
    private String prerequisites;
    private String postrequisites;

    private List<ThematicPlanModule> thematicPlan;
    private List<String> literatureList;
    private List<String> internetResources;
    private List<String> softwareUsed;
    private String disciplinePolicyText;
    private AssessmentCriteria assessmentCriteria;
    private List<String> examinationQuestions;
    private String generatedRawText;
}