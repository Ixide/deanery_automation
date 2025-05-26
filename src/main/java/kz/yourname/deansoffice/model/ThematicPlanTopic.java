package kz.yourname.deansoffice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThematicPlanTopic {
    private String topicNumberInModule;
    private String generalTopicTitle;
    private String lectureTheme;
    private String practicalTheme;
    private List<String> tasks = new ArrayList<>();
    private String srspTheme;
    private List<String> srspTasksList = new ArrayList<>();

    private String lectureHours;
    private String seminarHours;
    private String srspAndSrsHours;

    private List<String> roCovered = new ArrayList<>();

    private List<String> lectureContent = new ArrayList<>();
    private List<String> practicalContent = new ArrayList<>();
}