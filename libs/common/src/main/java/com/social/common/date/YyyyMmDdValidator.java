package com.social.common.date;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public class YyyyMmDdValidator implements ConstraintValidator<ValidDate, String> {
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);  // ★

    @Override
    public boolean isValid(String v, ConstraintValidatorContext c) {
        if (v == null) return true; // null은 @NotNull이 담당
        try {
            LocalDate.parse(v, FMT);
            return true;
        }
        catch (DateTimeParseException e) { return false; }
    }
}
