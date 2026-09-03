package com.pixous.hrportal.modules.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigOptionRepository extends JpaRepository<ConfigOption, Long> {

    List<ConfigOption> findByOptionSetIdOrderBySortOrderAscLabelAsc(Long optionSetId);

    List<ConfigOption> findByOptionSetIdAndActiveTrueOrderBySortOrderAscLabelAsc(Long optionSetId);

    Optional<ConfigOption> findByOptionSetIdAndOptionCode(Long optionSetId, String optionCode);

    long countByOptionSetId(Long optionSetId);
}
