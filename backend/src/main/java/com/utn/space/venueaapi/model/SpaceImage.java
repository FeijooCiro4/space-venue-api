package com.utn.space.venueaapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
@Entity
@Table(name = "spaceimages")
@NoArgsConstructor
public class SpaceImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_space_images")
    private Integer idSpaceImages;

    @ManyToOne
    @JoinColumn(name = "idSpace")
    private Space space;

    @Column(name = "file_name")
    private String fileName;
    @Column(name = "url_image")
    private String urlImage;
    @Column(name = "date_send")
    private LocalDateTime dateSend;
}
