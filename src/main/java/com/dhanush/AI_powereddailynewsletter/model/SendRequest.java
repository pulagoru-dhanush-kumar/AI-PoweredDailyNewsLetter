package com.dhanush.AI_powereddailynewsletter.model;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

public record SendRequest(
        @NotEmpty List<@Email String> emails,
        Integer limit
) {
}