package com.example.myproject.repository;

import com.example.myproject.model.Match;
import com.example.myproject.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByUser1(User user1);
    List<Match> findByUser2(User user2);
    List<Match> findByUser1AndUser2(User user1, User user2);

    Optional<Match> findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
            Long user1Id, Long user2Id,
            Long reversedUser1Id, Long reversedUser2Id
    );
}
