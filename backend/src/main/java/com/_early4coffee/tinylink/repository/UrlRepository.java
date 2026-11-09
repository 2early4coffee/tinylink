package com._early4coffee.tinylink.repository;

import com._early4coffee.tinylink.entity.UrlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UrlRepository extends JpaRepository<UrlEntity, String> {

    /** Atomic increment, so concurrent clicks are never lost. Returns rows changed. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update UrlEntity u set u.clickCount = u.clickCount + 1 "
            + "where u.shortCode = :code and u.active = true")
    int incrementClicks(@Param("code") String code);
}
