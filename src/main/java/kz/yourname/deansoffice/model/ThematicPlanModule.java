package kz.yourname.deansoffice.model; // Замените на ваш пакет

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThematicPlanModule {
    private String moduleNumberAndName;
    private List<ThematicPlanTopic> topics = new ArrayList<>();
}