package com.pixous.hrportal.modules.community;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, CommunityMember.CommunityMemberId> {
    
    @Query("SELECT cm FROM CommunityMember cm WHERE cm.community.id = :communityId")
    List<CommunityMember> findByCommunity_Id(@Param("communityId") Long communityId);
    
    @Modifying
    @Query("DELETE FROM CommunityMember cm WHERE cm.community.id = :communityId AND cm.user.id = :userId")
    void deleteByCommunity_IdAndUser_Id(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Query("SELECT COUNT(cm) > 0 FROM CommunityMember cm WHERE cm.community.id = :communityId AND cm.user.id = :userId")
    boolean isMember(@Param("communityId") Long communityId, @Param("userId") Long userId);

    /**
     * Every group this person belongs to, in one query.
     *
     * <p>The chat listing walks every group and asked isMember for each one,
     * so opening Chat cost one query per group in the company and grew with
     * every group anyone created. Reading the memberships once and testing
     * against the set is the same answer for one round trip.
     */
    @Query("SELECT cm.community.id FROM CommunityMember cm WHERE cm.user.id = :userId")
    List<Long> findCommunityIdsByUserId(@Param("userId") Long userId);
}
