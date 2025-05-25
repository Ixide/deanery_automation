package kz.yourname.deansoffice.repository;

import kz.yourname.deansoffice.model.ExamTicket;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ExamTicketRepository extends MongoRepository<ExamTicket, String> {
    List<ExamTicket> findByDisciplineNameOrderByTicketNumberAsc(String disciplineName);
}