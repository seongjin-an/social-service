package com.social.profile.controller;

import com.social.common.Gender;
import com.social.common.date.ValidDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;


public record ProfileWriteRequest(

    @NotNull
    Gender gender,

    @ValidDate
    LocalDate birthday,

    @NotBlank
    String bio,

    List<String> tags,

    @NotNull
    Gender prefGender,

    @NotNull @Positive
    Integer prefAgeMin,
    @NotNull @Positive
    Integer prefAgeMax,

    @NotNull @Positive @Max(value = 10)
    Integer prefDistanceKm
) {

}
