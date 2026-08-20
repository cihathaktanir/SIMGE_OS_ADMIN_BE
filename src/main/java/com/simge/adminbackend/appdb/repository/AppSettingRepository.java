package com.simge.adminbackend.appdb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simge.adminbackend.appdb.model.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
