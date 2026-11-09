package com._early4coffee.tinylink.repository;

import com._early4coffee.tinylink.entity.ClickEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ClickRepository extends JpaRepository<ClickEntity, Long> {

    List<ClickEntity> findTop10ByShortCodeOrderByClickedAtDesc(String shortCode);

    /** Rows of [referrer, count]. */
    @Query("select c.referrer, count(c) from ClickEntity c "
            + "where c.shortCode = :code and c.referrer is not null group by c.referrer")
    List<Object[]> countByReferrer(@Param("code") String code);

    /** Newest click times first. Pass a Pageable to cap how many rows are read. */
    @Query("select c.clickedAt from ClickEntity c where c.shortCode = :code order by c.clickedAt desc")
    List<Instant> findClickTimes(@Param("code") String code, Pageable pageable);

    @Transactional
    @Modifying
    @Query("delete from ClickEntity c where c.clickedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Transactional
    @Modifying
    @Query("delete from ClickEntity c where c.shortCode = :code")
    int deleteAllByCode(@Param("code") String code);
}
