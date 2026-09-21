package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.records.SpaceImageDTO;
import com.utn.space.venueaapi.model.SpaceImage;
import com.utn.space.venueaapi.repository.SpaceImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpaceImageService {
    @Autowired
    SpaceImageRepository spaceImageRepository;
    @Autowired
    SpaceService spaceService;

    public List<SpaceImage> findAll(){
        return spaceImageRepository.findAll();
    }

    public SpaceImage findById(Integer id){
        return spaceImageRepository.findById(id).orElseThrow(()-> new IdNotFoundException("No se encontro la imagen buscada: ", id));
    }

    public void deleteById(Integer id){
        if(!spaceImageRepository.existsById(id)){
            throw new IdNotFoundException("No se encontro la imagen a eliminar: ", id);
        }
        spaceImageRepository.deleteById(id);
    }

    public void insertSpaceImage(SpaceImageDTO spaceImageDTO){
        if(spaceImageDTO.fileName().isBlank()){
            throw new InvalidDataException("Por favor ingrese un nombre para la imagen");
        }

        if(spaceImageDTO.urlImage().isBlank()){
            throw new InvalidDataException("Por favor ingrese un URL para la imagen");
        }

        SpaceImage spaceImageToInsert = new SpaceImage(
                null,
                spaceService.findById(spaceImageDTO.idSpace()),
                spaceImageDTO.fileName(),
                spaceImageDTO.urlImage(),
                spaceImageDTO.dateSend()
        );

        spaceImageRepository.save(spaceImageToInsert);
    }


    public void modifySpaceImage(Integer id, SpaceImageDTO spaceImageDTO){
        if(!spaceImageRepository.existsById(id)){
            throw new IdNotFoundException("No se encontro la imagen a actualizar: ", id);
        }

        if(spaceImageDTO.fileName().isBlank()){
            throw new InvalidDataException("Por favor ingrese un nombre para la imagen");
        }

        if(spaceImageDTO.urlImage().isBlank()){
            throw new InvalidDataException("Por favor ingrese un URL para la imagen");
        }

        SpaceImage spaceImageToInsert = new SpaceImage(
                id,
                spaceService.findById(spaceImageDTO.idSpace()),
                spaceImageDTO.fileName(),
                spaceImageDTO.urlImage(),
                spaceImageDTO.dateSend()
        );

        spaceImageRepository.save(spaceImageToInsert);
    }

    public List<SpaceImage> findAllBySpaceId(Integer spaceId){
        if(!spaceService.existsById(spaceId)){
            throw new IdNotFoundException("No se encontro el espacio del cual se quieren buscar imagenes: ", spaceId);
        }
        return spaceImageRepository.findAllBySpaceIdSpace(spaceId);
    }
}
