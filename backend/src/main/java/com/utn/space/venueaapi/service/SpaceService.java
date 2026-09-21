package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.model.records.SpaceDTO;
import com.utn.space.venueaapi.model.records.SpaceFilterDTO;
import com.utn.space.venueaapi.repository.SpaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class SpaceService {
    @Autowired
    SpaceRepository spaceRepository;
    @Autowired
    ConsumerService consumerService;
    @Autowired
    LocationService locationService;
    @Autowired
    CancellationPoliciesService cancellationPoliciesService;


    public List<Space> findAll(){
        return spaceRepository.findAll();
    }

    public List<Space> findAllActives(){
        return spaceRepository.findAllWithOutInactives();
    }

    public Boolean existsById(Integer id){
        return spaceRepository.existsByIdSpaceAndIsActiveTrue(id);
    }

    public Space findById(Integer id){
        return spaceRepository.findById(id).orElseThrow(()-> new IdNotFoundException("Space", id));
    }

    public void deleteById(Integer id){
        if(!spaceRepository.existsById(id)){
            throw new IdNotFoundException("Space", id);
        }
        //Este metodo efectua soft DELETE
        Space space = spaceRepository.findById(id).orElseThrow(() -> new IdNotFoundException("Space", id));
        space.setIsActive(false);
        spaceRepository.save(space);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertSpace(SpaceDTO spaceDTO){
        if(spaceDTO.nameSpace().isBlank()){
            throw new InvalidDataException("Por favor ingrese un nombre para su espacio");
        }

        if(spaceDTO.description().isBlank()){
            throw new InvalidDataException("Por favor ingrese una descripcion para su espacio");
        }

        if(spaceDTO.basePrice().compareTo(BigDecimal.ZERO) <= 0){
            throw new InvalidDataException("Por favor ingrese un precio valido");
        }

        Space spaceToInsert = new Space();
        spaceToInsert.setIdSpace(null);
        spaceToInsert.setConsumerOwner(consumerService.findById(spaceDTO.idConsumerOwner()));
        spaceToInsert.setLocation(locationService.findByLongitudeAndLatitude(spaceDTO.location().longitude(), spaceDTO.location().latitude()));
        spaceToInsert.setCancellationPolicies(cancellationPoliciesService.findByType(EPolicyType.valueOf(spaceDTO.cancellationPolicies())));
        spaceToInsert.setNameSpace(spaceDTO.nameSpace());
        spaceToInsert.setDescription(spaceDTO.description());
        spaceToInsert.setBasePrice(spaceDTO.basePrice());
        spaceToInsert.setPublicationDate(spaceDTO.publicationDate());
        spaceToInsert.setBufferTime(spaceDTO.bufferTime());
        spaceToInsert.setIsActive(true);

        spaceRepository.save(spaceToInsert);
    }


    @Transactional(rollbackFor = Exception.class)
    public void modifySpace(Integer id, SpaceDTO spaceDTO){
        if(!spaceRepository.existsById(id)){
            throw new IdNotFoundException("Space: ", id);
        }

        if(spaceDTO.nameSpace().isBlank()){
            throw new InvalidDataException("Por favor ingrese un nombre para el espacio");
        }

        if(spaceDTO.description().isBlank()){
            throw new InvalidDataException("Por favor ingrese una descripcion para el espacio");
        }

        if(spaceDTO.basePrice().compareTo(BigDecimal.ZERO) <= 0){
            throw new InvalidDataException("Por favor ingrese un precio valido");
        }

        Space spaceToInsert = new Space();
        spaceToInsert.setIdSpace(id);
        spaceToInsert.setConsumerOwner(consumerService.findById(spaceDTO.idConsumerOwner()));
        spaceToInsert.setLocation(locationService.findByLongitudeAndLatitude(spaceDTO.location().longitude(), spaceDTO.location().latitude()));
        spaceToInsert.setCancellationPolicies(cancellationPoliciesService.findByType(EPolicyType.valueOf(spaceDTO.cancellationPolicies())));
        spaceToInsert.setNameSpace(spaceDTO.nameSpace());
        spaceToInsert.setDescription(spaceDTO.description());
        spaceToInsert.setBasePrice(spaceDTO.basePrice());
        spaceToInsert.setPublicationDate(spaceDTO.publicationDate());
        spaceToInsert.setBufferTime(spaceDTO.bufferTime());
        
        spaceRepository.save(spaceToInsert);
    }

    //Este metodo maneja solo espacios activos
    public List<Space> findAllByFields(SpaceFilterDTO spaceFilterDTO){
        if((spaceFilterDTO.idConsumerOwner() != null) && !consumerService.existsById(spaceFilterDTO.idConsumerOwner())){
            throw new IdNotFoundException("Consumer",spaceFilterDTO.idConsumerOwner());
        }

        if((spaceFilterDTO.idLocation() != null) && !locationService.existsById(spaceFilterDTO.idLocation())){
            throw new IdNotFoundException("Location",spaceFilterDTO.idLocation());
        }

        //Filtro inicial de la base de datos
        List<Space> spaces = spaceRepository.findAllByFields(
                spaceFilterDTO.idConsumerOwner(),
                spaceFilterDTO.minPrice(),
                spaceFilterDTO.maxPrice(),
                spaceFilterDTO.nameSpace(),
                spaceFilterDTO.idLocation());//Sigo filtrando por localizacion para poder filtrar por lugares como un shpping.

        //Se hace un filtro por proximidad al usuario, solo si este mando latitud y longitud
        if(spaceFilterDTO.lat() != null && spaceFilterDTO.lng() != null){
            //Si no encuentra un radio de filtrado en el DTO pongo 5Km de base
            BigDecimal maxRadious = spaceFilterDTO.radious() != null ? spaceFilterDTO.radious() : new BigDecimal("5.0");
            //Se usa isSpaceNearBy para filtrar la lista de espacios, primero filtro los espacios que tengan datos de ubicacion incompletos para evitar errores
            spaces = spaces.stream()
                    .filter(space ->space.getLocation()!= null && space.getLocation().getLatitude() != null && space.getLocation().getLongitude() != null)
                    .filter(space -> locationService.isSpaceNearby(spaceFilterDTO.lat(),spaceFilterDTO.lng(),maxRadious,space))
                    .toList();
        }

        return spaces;
    }


    //Este metodo considera espacios inactivos
    public List<Space> findAllByFieldsWithInactives(SpaceFilterDTO spaceFilterDTO){
        if((spaceFilterDTO.idConsumerOwner() != null) && !consumerService.existsById(spaceFilterDTO.idConsumerOwner())){
            throw new IdNotFoundException("Consumer",spaceFilterDTO.idConsumerOwner());
        }

        if((spaceFilterDTO.idLocation() != null) && !locationService.existsById(spaceFilterDTO.idLocation())){
            throw new IdNotFoundException("Location",spaceFilterDTO.idLocation());
        }

        //Filtro inicial de la base de datos
        List<Space> spaces = spaceRepository.findAllByFieldsWithInactives(
                spaceFilterDTO.idConsumerOwner(),
                spaceFilterDTO.minPrice(),
                spaceFilterDTO.maxPrice(),
                spaceFilterDTO.nameSpace(),
                spaceFilterDTO.idLocation());//Sigo filtrando por localizacion para poder filtrar por lugares como un shpping.

        //Se hace un filtro por proximidad al usuario, solo si este mando latitud y longitud
        if(spaceFilterDTO.lat() != null && spaceFilterDTO.lng() != null){
            //Si no encuentra un radio de filtrado en el DTO pongo 5Km de base
            BigDecimal maxRadious = spaceFilterDTO.radious() != null ? spaceFilterDTO.radious() : new BigDecimal("5.0");
            //Se usa isSpaceNearBy para filtrar la lista de espacios, primero filtro los espacios que tengan datos de ubicacion incompletos para evitar errores
            spaces = spaces.stream()
                    .filter(space ->space.getLocation()!= null && space.getLocation().getLatitude() != null && space.getLocation().getLongitude() != null)
                    .filter(space -> locationService.isSpaceNearby(spaceFilterDTO.lat(),spaceFilterDTO.lng(),maxRadious,space))
                    .toList();
        }

        return spaces;
    }

    public List<Space> findAllForOwner(){
        Integer loggedOwnerId = consumerService.getLoggedConsumerId();
        SpaceFilterDTO auxDTO = new SpaceFilterDTO(
                loggedOwnerId,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return findAllByFieldsWithInactives(auxDTO);
    }

    public List<Space> findAllByFieldsForOwner(SpaceFilterDTO spaceFilterDTO){
        Integer loggedOwnerId = consumerService.getLoggedConsumerId();
        SpaceFilterDTO auxDTO = new SpaceFilterDTO(
                loggedOwnerId,
                spaceFilterDTO.idLocation(),
                spaceFilterDTO.nameSpace(),
                spaceFilterDTO.minPrice(),
                spaceFilterDTO.maxPrice(),
                spaceFilterDTO.lat(),
                spaceFilterDTO.lng(),
                spaceFilterDTO.radious()
        );

        return findAllByFieldsWithInactives(auxDTO);
    }

    @Transactional
    public void deleteOwnedSpace(Integer id){
        Integer loggedOwnerId = consumerService.getLoggedConsumerId();
        Space spaceToDelete = findById(id);

        if(!Objects.equals(spaceToDelete.getConsumerOwner().getIdConsumer(), loggedOwnerId)){
            throw new InvalidDataException("Debe ser duenio de el espacio que desdea eliminar");
        }
        deleteById(id);
    }

    @Transactional
    public Space insertOwnedSpace(SpaceDTO spaceDTO) {
        Space space = new Space();

        space.setNameSpace(spaceDTO.nameSpace());
        space.setDescription(spaceDTO.description());
        space.setBasePrice(spaceDTO.basePrice());
        space.setBufferTime(spaceDTO.bufferTime());
        space.setIsActive(true);

        space.setPublicationDate(java.time.LocalDate.now());

        // Coordenadas de ubicación
        if (spaceDTO.location() != null) {
            Location nuevaLocacion = new Location();
            nuevaLocacion.setLatitude(spaceDTO.location().latitude());
            nuevaLocacion.setLongitude(spaceDTO.location().longitude());

            space.setLocation(nuevaLocacion);
        } else {
            throw new IllegalArgumentException("La ubicación geográfica es obligatoria.");
        }

        if (spaceDTO.cancellationPolicies() != null) {
            EPolicyType tipoEnum = EPolicyType.valueOf(spaceDTO.cancellationPolicies().toUpperCase());
            CancellationPolicies politicaBD = cancellationPoliciesService.findByType(tipoEnum);
            space.setCancellationPolicies(politicaBD);
        }

        if (spaceDTO.services() != null && !spaceDTO.services().isEmpty()) {
            List<SpaceServiceItem> items = spaceDTO.services().stream().map(sDto -> {
                SpaceServiceItem item = new SpaceServiceItem();
                item.setDescription(sDto.description());
                item.setPrice(sDto.price());
                item.setIsActive(true);
                item.setSpace(space);
                return item;
            }).toList();
            space.setServices(items);
        }

        space.setConsumerOwner(consumerService.findById(consumerService.getLoggedConsumerId()));

        return spaceRepository.save(space);
    }

    @Transactional
    public void modifyOwnedSpace(Integer id, SpaceDTO spaceDTO){
        Integer loggedOwnerId = consumerService.getLoggedConsumerId();
        Space spaceToModify = findById(id);

        if(!Objects.equals(spaceToModify.getConsumerOwner().getIdConsumer(), loggedOwnerId)){
            throw new InvalidDataException("Debe ser duenio de el espacio que desea modificar");
        }

        SpaceDTO spaceDTOAux = new SpaceDTO(
                id,
                spaceDTO.idConsumerOwner(),
                spaceDTO.location(),
                spaceDTO.cancellationPolicies(),
                spaceDTO.nameSpace(),
                spaceDTO.description(),
                spaceDTO.basePrice(),
                spaceDTO.publicationDate(),
                spaceDTO.bufferTime(),
                false, //De base cualquier modificacion hace que el espacio requiera una nueva verificacion
                spaceDTO.services()
        );

        modifySpace(id, spaceDTOAux);
    }

}