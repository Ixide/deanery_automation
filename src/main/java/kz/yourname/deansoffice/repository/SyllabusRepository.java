package kz.yourname.deansoffice.repository;

import kz.yourname.deansoffice.model.Syllabus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SyllabusRepository extends MongoRepository<Syllabus, String> {
    List<Syllabus> findByDisciplineInfoDisciplineName(String disciplineName);
}