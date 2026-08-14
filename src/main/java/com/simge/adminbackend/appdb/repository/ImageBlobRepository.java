package com.simge.adminbackend.appdb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simge.adminbackend.appdb.model.ImageBlob;

public interface ImageBlobRepository extends JpaRepository<ImageBlob, String> {
}
