package com.teamora.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ClaimReceiptRepository extends JpaRepository<ClaimReceipt, UUID> {

    /** Attach the receipt bytes to an already-inserted claim row. */
    @Modifying
    @Query("update ClaimReceipt r set r.photo = :photo where r.id = :id")
    int attach(@Param("id") UUID id, @Param("photo") byte[] photo);
}
