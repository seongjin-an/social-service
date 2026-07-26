package com.social.message.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChannelRequest(
    @NotBlank @Size(max = 100) String title
) {}
