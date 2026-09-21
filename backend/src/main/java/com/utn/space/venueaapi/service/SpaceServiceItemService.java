package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.model.SpaceServiceItem;
import com.utn.space.venueaapi.model.records.SpaceServiceItemDTO;
import com.utn.space.venueaapi.repository.SpaceServiceItemRepository;
import com.utn.space.venueaapi.service.mappers.SpaceServiceItemMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor
@Service
public class SpaceServiceItemService {
    @Autowired
    private final SpaceServiceItemRepository spaceServiceItemRepository;
    @Autowired
    private final SpaceService spaceService;
    @Autowired
    private final ConsumerService consumerService;

    public SpaceServiceItem findById(Integer id){
        return spaceServiceItemRepository.findById(id).orElseThrow(() -> new IdNotFoundException("Servicio Catálogo", id));
    }

    public List<SpaceServiceItemDTO> listOfServicesFromSpace(Integer id){
        return spaceServiceItemRepository.findAllSpaceServicesBySpaceId(id);
    }

    public List<SpaceServiceItemDTO> ConsumerlistOfServicesFromSpace(Integer id){
        Integer currentConsumerId = consumerService.getLoggedConsumerId();

        if(!spaceService.findById(id).getConsumerOwner().getIdConsumer().equals(currentConsumerId)){
            //Si no soy el dueño del espacio, se filtraran espacios inactivos
            List<SpaceServiceItemDTO> spaceServiceItemDTOs = spaceServiceItemRepository.findAllSpaceServicesBySpaceId(id).stream()
                    .filter(spaceServiceItemDTO -> spaceService.findById(spaceServiceItemDTO.idSpace()).getIsActive()).toList();

            //Luego se filtran Servicios inactivos
            spaceServiceItemDTOs = spaceServiceItemDTOs.stream().filter(spaceServiceItemDTO -> spaceServiceItemDTO.isActive()).toList();

            return spaceServiceItemDTOs;
        }
        //Si soy el dueño no filtro nada
        return spaceServiceItemRepository.findAllSpaceServicesBySpaceId(id);
    }

    @Transactional
    public void insertServiceItem(SpaceServiceItemDTO serviceItemDTO){
        if(serviceItemDTO.price().compareTo(BigDecimal.ZERO) <= 0) throw new InvalidDataException("No se permiten numeros negativos en el precio de un servicio");

        Space space = spaceService.findById(serviceItemDTO.idSpace());

        SpaceServiceItem serviceItem = SpaceServiceItemMapper.toEntity(serviceItemDTO, space);

        spaceServiceItemRepository.save(serviceItem);
    }

    @Transactional
    public void insertServiceItemOwner(SpaceServiceItemDTO serviceItemDTO){
        Integer currentConsumerId = consumerService.getLoggedConsumerId();

        if(!spaceService.findById(serviceItemDTO.idSpace()).getConsumerOwner().getIdConsumer().equals(currentConsumerId)){
            throw new InvalidDataException("No puede insertar servicios en un espacio del que no es duenio");
        }

        insertServiceItem(serviceItemDTO);
    }

    @Transactional
    public void updateServiceItem(Integer id, SpaceServiceItemDTO serviceItemDTO){
        if(!spaceServiceItemRepository.existsServiceItemInSpace(id, serviceItemDTO.idSpace())){
            throw new IdNotFoundException("No se ha encontrado el servicio a modificar en el espacio: ", id);
        }

        if(serviceItemDTO.price().compareTo(BigDecimal.ZERO) <= 0) throw new InvalidDataException("No se permiten numeros negativos en modificacion del precio de un servicio");

        SpaceServiceItem serviceItem = SpaceServiceItemMapper.toEntity(
                serviceItemDTO,
                id,
                spaceService.findById(serviceItemDTO.idSpace()));

        spaceServiceItemRepository.save(serviceItem);
    }

    @Transactional
    public void updateServiceItemOwner(Integer id, SpaceServiceItemDTO serviceItemDTO){
        Integer currentConsumerId = consumerService.getLoggedConsumerId();

        if(!spaceService.findById(serviceItemDTO.idSpace()).getConsumerOwner().getIdConsumer().equals(currentConsumerId)){
            throw new InvalidDataException("No puede modificar servicios en un espacio del que no es duenio");
        }

        updateServiceItem(id,serviceItemDTO);
    }

    @Transactional
    public void deleteServiceItemOwner(Integer id, Integer idSpace) {
        Integer currentConsumerId = consumerService.getLoggedConsumerId();

        // LOG DE SEGURIDAD
        System.out.println("DEBUG SERVICE: Intento de borrado por usuario ID: " + currentConsumerId);

        // Obtener dueño
        var space = spaceService.findById(idSpace);
        Integer ownerId = space.getConsumerOwner().getIdConsumer();

        System.out.println("DEBUG SERVICE: Dueño del espacio es ID: " + ownerId);

        if (!ownerId.equals(currentConsumerId)) {
            System.out.println("DEBUG SERVICE: ERROR - El usuario no es dueño. Abortando.");
            throw new InvalidDataException("No puede eliminar servicios en un espacio del que no es dueño");
        }

        deleteServiceItem(id, idSpace);
    }

    @Transactional
    public void deleteServiceItem(Integer id, Integer idSpace) {
        System.out.println("DEBUG SERVICE: Verificando existencia de servicio " + id + " en espacio " + idSpace);

        boolean exists = spaceServiceItemRepository.existsServiceItemInSpace(id, idSpace);

        if (!exists) {
            System.out.println("DEBUG SERVICE: ERROR - No se encontró la relación Servicio-Espacio. Abortando.");
            throw new IdNotFoundException("No se ha encontrado el servicio a eliminar: ", id);
        }

        System.out.println("DEBUG SERVICE: Ejecutando borrado real en base de datos...");
        System.out.println("DEBUG: El ID que voy a borrar es: " + id);
        spaceServiceItemRepository.deleteById(id);
        System.out.println("DEBUG SERVICE: Borrado ejecutado con éxito.");
    }
}
