package com.social.profile.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.social.common.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;


public record ProfileWriteRequest(

    @NotNull
    Gender gender,

    @NotNull
    @Past
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMdd")
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
