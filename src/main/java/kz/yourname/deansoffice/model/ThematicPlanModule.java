package kz.yourname.deansoffice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThematicPlanModule {
    private String moduleNumberAndName;
    private List<ThematicPlanTopic> topics;
}