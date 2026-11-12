package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.records.SpaceImageDTO;
import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.model.SpaceImage;
import com.utn.space.venueaapi.repository.SpaceImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SpaceImageService {
    @Autowired
    SpaceImageRepository spaceImageRepository;
    @Autowired
    SpaceService spaceService;
    @Autowired
    ImageStorageService imageStorageService;

    public List<SpaceImage> findAll(){
        return spaceImageRepository.findAll();
    }

    public SpaceImage findById(Integer id){
        return spaceImageRepository.findById(id).orElseThrow(()-> new IdNotFoundException("la imagen", id));
    }

    @Transactional
    public void deleteById(Integer id){
        SpaceImage spaceImage = spaceImageRepository.findById(id).orElseThrow(()-> new IdNotFoundException("la imagen", id));
        spaceService.onContentChangedByOwner(spaceImage.getSpace());
        spaceImageRepository.deleteById(id);

        if(spaceImage.getUrlImage() != null && spaceImage.getUrlImage().startsWith("media/")){
            imageStorageService.deleteObject(spaceImage.getUrlImage());
        }
    }

    @Transactional
    public void insertSpaceImage(SpaceImageDTO spaceImageDTO){
        if(spaceImageDTO.fileName().isBlank()){
            throw new InvalidDataException("Por favor ingrese un nombre para la imagen.");
        }

        if(spaceImageDTO.urlImage().isBlank()){
            throw new InvalidDataException("Por favor ingrese una URL para la imagen.");
        }

        // urlImage es la key devuelta por /upload-url; evita asociar al espacio imágenes de otro
        if(!spaceImageDTO.urlImage().startsWith(ImageStorageService.keyPrefixForSpace(spaceImageDTO.idSpace()))){
            throw new InvalidDataException("La imagen no corresponde a este espacio.");
        }

        Space space = spaceService.findById(spaceImageDTO.idSpace());
        spaceService.onContentChangedByOwner(space);

        SpaceImage spaceImageToInsert = new SpaceImage(
                null,
                space,
                spaceImageDTO.fileName(),
                spaceImageDTO.urlImage(),
                spaceImageDTO.dateSend()
        );

        spaceImageRepository.save(spaceImageToInsert);
    }


    @Transactional
    public void modifySpaceImage(Integer id, SpaceImageDTO spaceImageDTO){
        if(!spaceImageRepository.existsById(id)){
            throw new IdNotFoundException("la imagen", id);
        }

        if(spaceImageDTO.fileName().isBlank()){
            throw new InvalidDataException("Por favor ingrese un nombre para la imagen.");
        }

        if(spaceImageDTO.urlImage().isBlank()){
            throw new InvalidDataException("Por favor ingrese una URL para la imagen.");
        }

        // urlImage es la key devuelta por /upload-url; evita asociar al espacio imágenes de otro
        if(!spaceImageDTO.urlImage().startsWith(ImageStorageService.keyPrefixForSpace(spaceImageDTO.idSpace()))){
            throw new InvalidDataException("La imagen no corresponde a este espacio.");
        }

        Space space = spaceService.findById(spaceImageDTO.idSpace());
        spaceService.onContentChangedByOwner(space);

        SpaceImage spaceImageToInsert = new SpaceImage(
                id,
                space,
                spaceImageDTO.fileName(),
                spaceImageDTO.urlImage(),
                spaceImageDTO.dateSend()
        );

        spaceImageRepository.save(spaceImageToInsert);
    }

    public List<SpaceImage> findAllBySpaceId(Integer spaceId){
        if(!spaceService.existsByIdIncludingInactive(spaceId)){
            throw new IdNotFoundException("el espacio", spaceId);
        }
        return spaceImageRepository.findAllBySpaceIdSpace(spaceId);
    }
}
