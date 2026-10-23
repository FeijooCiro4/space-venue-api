package com.utn.space.venueaapi.model.records;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.CreateAdmin;
import com.utn.space.venueaapi.model.flags.CreateOwned;
import com.utn.space.venueaapi.model.flags.Update;
import com.utn.space.venueaapi.model.flags.UpdateAdmin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;

import java.math.BigDecimal;

// SpaceDTO se valida con grupos (@Validated(Create.class), etc.) y @Valid propaga ese mismo grupo,
// por eso cada regla tiene que declarar los grupos; si no, solo correrian con Default y nunca se aplicarian.
//Esto es para corregir errores de validacion y de precision en la ubicación de un espacio.
public record LocationDTO(
        @NotNull(groups = {Default.class, Create.class, CreateAdmin.class, CreateOwned.class, Update.class, UpdateAdmin.class})
        @DecimalMin(value = "-90", groups = {Default.class, Create.class, CreateAdmin.class, CreateOwned.class, Update.class, UpdateAdmin.class})
        @DecimalMax(value = "90", groups = {Default.class, Create.class, CreateAdmin.class, CreateOwned.class, Update.class, UpdateAdmin.class})
        @Digits(integer = 2, fraction = 6, message = "La latitud admite hasta 6 decimales",
                groups = {Default.class, Create.class, CreateAdmin.class, CreateOwned.class, Update.class, UpdateAdmin.class})
        BigDecimal latitude,

        @NotNull(groups = {Default.class, Create.class, CreateAdmin.class, CreateOwned.class, Update.class, UpdateAdmin.class})
        @DecimalMin(value = "-180", groups = {Default.class, Create.class, CreateAdmin.class, CreateOwned.class, Update.class, UpdateAdmin.class})
        @DecimalMax(value = "180", groups = {Default.class, Create.class, CreateAdmin.class, CreateOwned.class, Update.class, UpdateAdmin.class})
        @Digits(integer = 3, fraction = 6, message = "La longitud admite hasta 6 decimales",
                groups = {Default.class, Create.class, CreateAdmin.class, CreateOwned.class, Update.class, UpdateAdmin.class})
        BigDecimal longitude
) {}
