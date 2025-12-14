package com.example.myproject.repository;

import com.example.myproject.model.WeddingParticipant;
import com.example.myproject.model.enums.WeddingParticipantRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WeddingParticipantRepository extends JpaRepository<WeddingParticipant, Long> {

    Optional<WeddingParticipant> findByWedding_IdAndUser_Id(Long weddingId, Long userId);

    boolean existsByWedding_IdAndUser_Id(Long weddingId, Long userId);

    // ---- IMPORTANT: lock row when doing JOIN/LEAVE/ROLE updates to prevent race conditions
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wp from WeddingParticipant wp where wp.wedding.id = :weddingId and wp.user.id = :userId")
    Optional<WeddingParticipant> findForUpdate(@Param("weddingId") Long weddingId, @Param("userId") Long userId);

    // Active participants (not left, not blocked)
    List<WeddingParticipant> findByWedding_IdAndLeftAtIsNullAndBlockedFalseOrderByJoinedAtAsc(Long weddingId);

    // Active participants by role(s)
    List<WeddingParticipant> findByWedding_IdAndLeftAtIsNullAndBlockedFalseAndRoleInWeddingInOrderByJoinedAtAsc(
            Long weddingId,
            Collection<WeddingParticipantRole> roles
    );

    // All history in wedding
    List<WeddingParticipant> findByWedding_IdOrderByJoinedAtDesc(Long weddingId);

    // User history across weddings
    List<WeddingParticipant> findByUser_IdOrderByJoinedAtDesc(Long userId);

    long countByWedding_Id(Long weddingId);

    long countByWedding_IdAndLeftAtIsNullAndBlockedFalse(Long weddingId);

    long countByWedding_IdAndBlockedTrue(Long weddingId);

    long countByWedding_IdAndRoleInWedding(Long weddingId, WeddingParticipantRole role);

    List<WeddingParticipant> findByWedding_IdAndRoleInWedding(Long weddingId, WeddingParticipantRole role);

    // Useful for cleanup/monitoring
    List<WeddingParticipant> findByWedding_IdAndLeftAtIsNullAndBlockedFalseAndJoinedAtBefore(
            Long weddingId,
            LocalDateTime joinedBefore
    );

    // Permission helper: actor is owner/coOwner in participants table (even if Wedding.coOwners isn't synced yet)
    boolean existsByWedding_IdAndUser_IdAndBlockedFalseAndRoleInWeddingIn(
            Long weddingId,
            Long userId,
            Collection<WeddingParticipantRole> roles
    );
}