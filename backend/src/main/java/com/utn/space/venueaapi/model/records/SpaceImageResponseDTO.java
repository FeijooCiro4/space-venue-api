package com.utn.space.venueaapi.model.records;

import java.time.LocalDateTime;

public record SpaceImageResponseDTO(Integer idSpaceImages, SpaceResponseDTO space,
                                    String fileName, String urlImage, LocalDateTime dateSend) {}
