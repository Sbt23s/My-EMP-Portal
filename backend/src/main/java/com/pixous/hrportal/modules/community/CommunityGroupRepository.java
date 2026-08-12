package com.pixous.hrportal.modules.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityGroupRepository extends JpaRepository<CommunityGroup, Long> {

    @Query("SELECT cm.community FROM CommunityMember cm WHERE cm.user.id = :userId")
    List<CommunityGroup> findByMemberUserId(@Param("userId") Long userId);

    boolean existsByNameIgnoreCase(String name);

    List<CommunityGroup> findByCreatedBy_Id(Long creatorId);

    Optional<CommunityGroup> findByName(String name);
}
