package com.social.common.date;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({ FIELD, PARAMETER }) @
Retention(RUNTIME)
@Constraint(validatedBy = YyyyMmDdValidator.class)
public @interface ValidDate {
    String message() default "유효한 날짜(yyyyMMdd)가 아닙니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
