package com.simge.adminbackend.appdb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simge.adminbackend.appdb.model.HomeSection;
import com.simge.adminbackend.appdb.model.HomeSectionItem;

public interface HomeSectionItemRepository extends JpaRepository<HomeSectionItem, Long> {

    List<HomeSectionItem> findBySectionOrderBySortOrderAscIdAsc(HomeSection section);
}
