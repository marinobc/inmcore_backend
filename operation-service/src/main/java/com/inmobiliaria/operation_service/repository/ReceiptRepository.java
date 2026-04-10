package com.inmobiliaria.operation_service.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.inmobiliaria.operation_service.model.Receipt;

/**
 * MongoDB repository for Receipt documents.
 */
@Repository
public interface ReceiptRepository extends MongoRepository<Receipt, String> {

    /**
     * Returns all receipts for a given operation, ordered by upload date
     * descending.
     * Used for the operation detail receipt list.
     */
    List<Receipt> findByOperationIdOrderByUploadedAtDesc(String operationId);

    /**
     * Counts how many receipts are associated with a given operation.
     */
    long countByOperationId(String operationId);

    /**
     * Checks whether a receipt with the given ID belongs to a specific operation.
     * Used before deleting to verify ownership.
     */
    boolean existsByIdAndOperationId(String id, String operationId);
}