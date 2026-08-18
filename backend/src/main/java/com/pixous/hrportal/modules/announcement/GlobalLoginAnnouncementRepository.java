package com.pixous.hrportal.modules.announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalLoginAnnouncementRepository extends JpaRepository<GlobalLoginAnnouncement, Long> {

    Optional<GlobalLoginAnnouncement> findFirstByStatusOrderByPublishedAtDesc(String status);

    List<GlobalLoginAnnouncement> findByStatusNotOrderByCreatedAtDesc(String status);

    List<GlobalLoginAnnouncement> findByStatus(String status);
}
