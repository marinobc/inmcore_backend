package com.inmobiliaria.operation_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Form-data fields sent alongside the file when attaching a receipt.
 *
 * The request must be {@code multipart/form-data}:
 * - {@code file} → the actual PDF / image (MultipartFile)
 * - {@code amount} → payment amount
 * - {@code currency} → ISO 4217 code
 * - {@code paymentDate} → ISO-8601 datetime
 * - {@code concept} → human-readable description
 */
@Data
public class ReceiptUploadRequest {

    @NotNull(message = "Payment amount is required.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required.")
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO 4217 code (e.g. USD, BOB).")
    private String currency;

    @NotNull(message = "Payment date is required.")
    private LocalDateTime paymentDate;

    @NotBlank(message = "Concept is required.")
    @Size(max = 255, message = "Concept cannot exceed 255 characters.")
    private String concept;
}