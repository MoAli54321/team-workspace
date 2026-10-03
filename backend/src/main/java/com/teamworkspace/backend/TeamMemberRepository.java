package com.teamworkspace.backend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamId(Long teamId);

    List<TeamMember> findByUserId(Long userId);

    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    Optional<TeamMember> findByUserIdAndTeamId(Long userId, Long teamId);
}
