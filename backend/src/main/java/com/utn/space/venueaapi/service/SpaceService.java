package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.model.records.SpaceDTO;
import com.utn.space.venueaapi.model.records.SpaceFilterDTO;
import com.utn.space.venueaapi.repository.CommentRepository;
import com.utn.space.venueaapi.repository.SpaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class SpaceService {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    SpaceRepository spaceRepository;
    @Autowired
    ConsumerService consumerService;
    @Autowired
    LocationService locationService;
    @Autowired
    CancellationPoliciesService cancellationPoliciesService;
    @Autowired
    CommentRepository commentRepository;


    public List<Space> findAll(){
        return spaceRepository.findAll();
    }

    public List<Space> findAllActives(){
        return spaceRepository.findAllWithOutInactives();
    }

    public Boolean existsById(Integer id){
        return spaceRepository.existsByIdSpaceAndIsActiveTrue(id);
    }

    public Boolean existsByIdIncludingInactive(Integer id){
        return spaceRepository.existsById(id);
    }

    public Space findById(Integer id){
        return spaceRepository.findById(id).orElseThrow(()-> new IdNotFoundException("el espacio", id));
    }

    public Space findByIdForUpdate(Integer id) {
        Space space = spaceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IdNotFoundException("el espacio", id));
        entityManager.refresh(space, LockModeType.PESSIMISTIC_WRITE);
        return space;
    }

    public void deleteById(Integer id){
        if(!spaceRepository.existsById(id)){
            throw new IdNotFoundException("el espacio", id);
        }
        //Este metodo efectua soft DELETE
        Space space = spaceRepository.findById(id).orElseThrow(() -> new IdNotFoundException("el espacio", id));
        space.setIsActive(false);
        space.setDeactivationReason(ESpaceDeactivationReason.ADMIN);
        spaceRepository.save(space);
    }

    //Permite al admin activar o desactivar un espacio (aprobar o dar de baja)
    @Transactional
    public void setActive(Integer id, Boolean active){
        Space space = findById(id);
        if (Boolean.TRUE.equals(active) && isDeletedByOwner(space)) {
            throw new InvalidDataException("No se puede activar un espacio que fue eliminado por su dueño.");
        }
        space.setIsActive(active);
        space.setDeactivationReason(Boolean.TRUE.equals(active) ? null : ESpaceDeactivationReason.ADMIN);
        spaceRepository.save(space);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertSpace(SpaceDTO spaceDTO){
        if(spaceDTO.nameSpace().isBlank()){
            throw new InvalidDataException("Por favor ingrese un nombre para su espacio.");
        }

        if(spaceDTO.description().isBlank()){
            throw new InvalidDataException("Por favor ingrese una descripción para su espacio.");
        }

        if(spaceDTO.basePrice().compareTo(BigDecimal.ZERO) <= 0){
            throw new InvalidDataException("Por favor ingrese un precio válido.");
        }

        Space spaceToInsert = new Space();
        spaceToInsert.setIdSpace(null);
        spaceToInsert.setConsumerOwner(consumerService.findById(spaceDTO.idConsumerOwner()));
        spaceToInsert.setLocation(locationService.findByLongitudeAndLatitude(spaceDTO.location().longitude(), spaceDTO.location().latitude()));
        spaceToInsert.setCancellationPolicies(cancellationPoliciesService.findByType(parsearPolitica(spaceDTO.cancellationPolicies())));
        spaceToInsert.setNameSpace(spaceDTO.nameSpace());
        spaceToInsert.setDescription(spaceDTO.description());
        spaceToInsert.setBasePrice(spaceDTO.basePrice());
        spaceToInsert.setPublicationDate(spaceDTO.publicationDate());
        spaceToInsert.setBufferTime(spaceDTO.bufferTime());
        spaceToInsert.setIsActive(true);

        spaceRepository.save(spaceToInsert);
    }


    @Transactional(rollbackFor = Exception.class)
    public void modifySpace(Integer id, SpaceDTO spaceDTO) {
        Space space = findByIdForUpdate(id);
        applyEditableDetails(space, spaceDTO);
    }

    private void applyEditableDetails(Space space, SpaceDTO dto) {
        if (dto.idSpace() != null && !Objects.equals(dto.idSpace(), space.getIdSpace())) {
            throw new InvalidDataException("El ID del cuerpo no coincide con el espacio a modificar.");
        }
        if (dto.idConsumerOwner() != null
                && !Objects.equals(dto.idConsumerOwner(), space.getConsumerOwner().getIdConsumer())) {
            throw new InvalidDataException("No se puede transferir el propietario mediante la edición del espacio.");
        }
        if (dto.nameSpace() == null || dto.nameSpace().isBlank()
                || dto.description() == null || dto.description().isBlank()) {
            throw new InvalidDataException("El nombre y la descripción del espacio son obligatorios.");
        }
        if (dto.basePrice() == null || dto.basePrice().signum() <= 0
                || dto.bufferTime() == null || dto.bufferTime() <= 0) {
            throw new InvalidDataException("El precio y el tiempo entre alquileres deben ser positivos.");
        }
        if (dto.location() == null || dto.location().longitude() == null || dto.location().latitude() == null) {
            throw new InvalidDataException("La ubicación del espacio es obligatoria.");
        }
        final EPolicyType policy = parsearPolitica(dto.cancellationPolicies());
        Location location = resolveEditedLocation(space.getLocation(), dto.location().longitude(), dto.location().latitude());
        CancellationPolicies cancellation = cancellationPoliciesService.findByType(policy);
        space.setNameSpace(dto.nameSpace());
        space.setDescription(dto.description());
        space.setBasePrice(dto.basePrice());
        space.setBufferTime(dto.bufferTime());
        space.setLocation(location);
        space.setCancellationPolicies(cancellation);
        // Conservar propietario, actividad, publicación y catálogo. Los servicios tienen sus propias rutas.
    }

    //Se llama antes de agregar, modificar o borrar imágenes o servicios de un espacio.
    //Igual que al editar los datos: si el cambio lo hace el dueño, el espacio vuelve a quedar pendiente de aprobación.
    //Los cambios del admin no lo desactivan (como en modifySpace).
    public void onContentChangedByOwner(Space space) {
        if (!isLoggedUserOwner(space)) return;
        if (isDeletedByOwner(space)) {
            throw new InvalidDataException("No se puede modificar un espacio eliminado.");
        }
        space.setIsActive(false);
        markPendingApproval(space);
        spaceRepository.save(space);
    }

    private boolean isLoggedUserOwner(Space space) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        var owner = space.getConsumerOwner();
        return auth != null && owner != null && owner.getCredentials() != null
                && Objects.equals(owner.getCredentials().getUsername(), auth.getName());
    }

    //Si las coordenadas no cambiaron se conserva la ubicación actual; si cambiaron se crea una nueva
    //(no se modifica la existente porque podría estar compartida con otro espacio)
    private Location resolveEditedLocation(Location current, BigDecimal longitude, BigDecimal latitude) {
        if (current != null && current.getLongitude() != null && current.getLatitude() != null
                && current.getLongitude().compareTo(longitude) == 0
                && current.getLatitude().compareTo(latitude) == 0) {
            return current;
        }
        return locationService.create(longitude, latitude);
    }

    //Este metodo maneja solo espacios activos
    public List<Space> findAllByFields(SpaceFilterDTO spaceFilterDTO){
        if((spaceFilterDTO.idConsumerOwner() != null) && !consumerService.existsById(spaceFilterDTO.idConsumerOwner())){
            throw new IdNotFoundException("el usuario",spaceFilterDTO.idConsumerOwner());
        }

        if((spaceFilterDTO.idLocation() != null) && !locationService.existsById(spaceFilterDTO.idLocation())){
            throw new IdNotFoundException("la ubicación",spaceFilterDTO.idLocation());
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
            throw new IdNotFoundException("el usuario",spaceFilterDTO.idConsumerOwner());
        }

        if((spaceFilterDTO.idLocation() != null) && !locationService.existsById(spaceFilterDTO.idLocation())){
            throw new IdNotFoundException("la ubicación",spaceFilterDTO.idLocation());
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

        return withoutDeletedByOwner(findAllByFieldsWithInactives(auxDTO));
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

        return withoutDeletedByOwner(findAllByFieldsWithInactives(auxDTO));
    }

    //Los espacios eliminados por el dueño no se le muestran más en sus listados
    private List<Space> withoutDeletedByOwner(List<Space> spaces) {
        return spaces.stream().filter(space -> !isDeletedByOwner(space)).toList();
    }

    //Si un admin lo había desactivado se conserva ese motivo: editar no convierte la baja del admin en "pendiente de aprobación"
    private void markPendingApproval(Space space) {
        if (space.getDeactivationReason() != ESpaceDeactivationReason.ADMIN) {
            space.setDeactivationReason(ESpaceDeactivationReason.PENDING_APPROVAL);
        }
    }

    private boolean isDeletedByOwner(Space space) {
        return Boolean.TRUE.equals(space.getDeletedByOwner());
    }

    //Soft delete hecho por el dueño: queda inactivo y marcado como eliminado para distinguirlo de un espacio pendiente de aprobación
    @Transactional
    public void deleteOwnedSpace(Integer id){
        Integer loggedOwnerId = consumerService.getLoggedConsumerId();
        Space spaceToDelete = findById(id);

        if(!Objects.equals(spaceToDelete.getConsumerOwner().getIdConsumer(), loggedOwnerId)){
            throw new InvalidDataException("Debe ser dueño del espacio que desea eliminar.");
        }
        if (isDeletedByOwner(spaceToDelete)) {
            throw new InvalidDataException("El espacio ya fue eliminado.");
        }
        spaceToDelete.setIsActive(false);
        spaceToDelete.setDeletedByOwner(true);
        spaceToDelete.setDeactivationReason(ESpaceDeactivationReason.OWNER_DELETED);
        spaceRepository.save(spaceToDelete);
    }

    @Transactional
    public Space insertOwnedSpace(SpaceDTO spaceDTO) {
        Space space = new Space();

        space.setNameSpace(spaceDTO.nameSpace());
        space.setDescription(spaceDTO.description());
        space.setBasePrice(spaceDTO.basePrice());
        space.setBufferTime(spaceDTO.bufferTime());
        space.setIsActive(false); // Los espacios insertados por el dueño pasan a inactivos hasta que el admin los apruebe
        space.setDeactivationReason(ESpaceDeactivationReason.PENDING_APPROVAL);

        space.setPublicationDate(java.time.LocalDate.now());

        // Coordenadas de ubicación
        if (spaceDTO.location() != null) {
            Location nuevaLocacion = new Location();
            nuevaLocacion.setLatitude(spaceDTO.location().latitude());
            nuevaLocacion.setLongitude(spaceDTO.location().longitude());

            space.setLocation(nuevaLocacion);
        } else {
            throw new InvalidDataException("La ubicación geográfica es obligatoria.");
        }

        if (spaceDTO.cancellationPolicies() != null) {
            EPolicyType tipoEnum = parsearPolitica(spaceDTO.cancellationPolicies());
            CancellationPolicies politicaBD = cancellationPoliciesService.findByType(tipoEnum);
            space.setCancellationPolicies(politicaBD);
        }

        if (spaceDTO.services() != null && !spaceDTO.services().isEmpty()) {
            List<SpaceServiceItem> items = spaceDTO.services().stream().map(sDto -> {
                if (sDto.price() == null || sDto.price().signum() < 0) {
                    throw new InvalidDataException("El precio de un servicio no puede ser negativo.");
                }
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
    public void modifyOwnedSpace(Integer id, SpaceDTO spaceDTO) {
        Integer loggedOwnerId = consumerService.getLoggedConsumerId();
        Space space = findByIdForUpdate(id);
        if (!Objects.equals(space.getConsumerOwner().getIdConsumer(), loggedOwnerId)) {
            throw new AccessDeniedException("Debe ser dueño del espacio que desea modificar.");
        }
        if (isDeletedByOwner(space)) {
            throw new InvalidDataException("No se puede modificar un espacio eliminado.");
        }
        space.setIsActive(false);//Los espacios modificados pasan a inactivos hasta que el admin los apruebe nuevamente
        markPendingApproval(space);
        applyEditableDetails(space, spaceDTO);
    }


    //Convierte el texto recibido a la política; si no existe es un error del usuario, no del servidor
    private EPolicyType parsearPolitica(String valor) {
        try {
            return EPolicyType.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidDataException("La política de cancelación no es válida.");
        }
    }

    public void calcularRatingYCount(Space space){
        List<Comment> lista = commentRepository.findAllBySpaceIdSpace(space.getIdSpace());
        if(!lista.isEmpty()){
            space.setCommentCount(lista.size());
            int contador = 0;
            for(Comment comment : lista){
                contador += comment.getScore();
            }
            double promedio = (double) contador / lista.size();
            space.setRating(promedio);
            spaceRepository.updateRatingAndCount(space.getIdSpace(), promedio, lista.size());

        }
        else{
            space.setRating(0);
            space.setCommentCount(0);
            spaceRepository.updateRatingAndCount(space.getIdSpace(), 0, 0);
        }
    }
}
