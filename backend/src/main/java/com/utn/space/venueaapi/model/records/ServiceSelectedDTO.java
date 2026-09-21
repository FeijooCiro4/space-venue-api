package com.utn.space.venueaapi.model.records;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;


public record ServiceSelectedDTO(
        @NotNull(groups = Update.class)
        @PositiveOrZero
        Integer id,

        @NotNull(groups = {Create.class, Update.class})
        @Positive(groups = {Create.class, Update.class})
        BigDecimal priceAtReservation,

        @NotBlank(groups = {Create.class, Update.class})
        String descriptionFrozen,

        @NotNull(groups = {Create.class, Update.class})
        @Positive(groups = {Create.class, Update.class})
        Integer idReservation
){}