package com.pixous.hrportal.modules.community;

import com.pixous.hrportal.modules.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.io.Serializable;

@Entity
@Table(name = "community_members")
@Getter
@Setter
public class CommunityMember {

    @EmbeddedId
    private CommunityMemberId id = new CommunityMemberId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("communityId")
    @JoinColumn(name = "community_id")
    private CommunityGroup community;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "joined_at", insertable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Embeddable
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommunityMemberId implements Serializable {
        @Column(name = "community_id")
        private Long communityId;

        @Column(name = "user_id")
        private Long userId;
    }
}
