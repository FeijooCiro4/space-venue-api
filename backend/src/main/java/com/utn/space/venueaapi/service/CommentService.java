package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.Comment;
import com.utn.space.venueaapi.model.ECommentDeactivationReason;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.ReservationStatus;
import com.utn.space.venueaapi.model.records.CommentDTO;
import com.utn.space.venueaapi.repository.CommentRepository;
import com.utn.space.venueaapi.repository.ReservationRepository;
import com.utn.space.venueaapi.service.mappers.CommentMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentService {
    @Autowired
    CommentRepository commentRepository;
    @Autowired
    ConsumerService consumerService;
    @Autowired
    SpaceService spaceService;
    @Autowired
    ReservationService reservationService;
    @Autowired
    ReservationRepository reservationRepository;
    @Autowired
    NotificationService notificationService;

    public List<CommentDTO> findAll() {
        return commentRepository.findAllActive()
                .stream()
                .map(CommentMapper::mapToDTO)
                .toList();
    }

    public CommentDTO findById(Integer id) {
        return CommentMapper.mapToDTO(
                commentRepository.findActiveById(id)
                        .orElseThrow(() -> new IdNotFoundException("el comentario", id))
        );
    }

    public void deleteById(Integer id){
        if(!commentRepository.existsById(id)){
            throw new IdNotFoundException("el comentario", id);
        }
        commentRepository.deleteById(id);
    }

    @Transactional
    public void adminDeletion(Integer id){
        if(!commentRepository.existsById(id)){
            throw new IdNotFoundException("el comentario", id);
        }

        Comment comment = commentRepository.findById(id).orElseThrow(()-> new IdNotFoundException("el comentario", id));

        if(!comment.getIsActive()){
            throw new InvalidDataException("El comentario ya se encuentra desactivado.");
        }

        comment.setIsActive(false);
        comment.setDeactivationReason(ECommentDeactivationReason.ADMIN);
        commentRepository.save(comment);

        notificationService.createNotification(
                comment.getConsumer(),
                "Su comentario en el espacio " + comment.getSpace().getNameSpace() + " que decia '" + comment.getDescription() + "' ha sido eliminado por un administrador."
        );
    }

    public void deleteByIdCustomer(Integer id){
        Comment commentToDelete = commentRepository.findById(id).orElseThrow(()-> new IdNotFoundException("el comentario", id));
        //Logica para que un usuario solo elimine comentarios propios
        if(!commentToDelete.getConsumer().getIdConsumer().equals(consumerService.getLoggedConsumerId())){
            throw new AccessDeniedException("No se puede eliminar un comentario que no es propio");
        }
        commentRepository.deleteById(id);
    }

    public void consumerDeletion(Integer id){
        Comment commentToDelete = commentRepository.findById(id).orElseThrow(()-> new IdNotFoundException("el comentario", id));
        if(!commentToDelete.getConsumer().getIdConsumer().equals(consumerService.getLoggedConsumerId())){
            throw new AccessDeniedException("No se puede eliminar un comentario que no es propio");
        }

        if(!commentToDelete.getIsActive()){
            throw new InvalidDataException("El comentario ya se encuentra desactivado.");
        }

        commentToDelete.setIsActive(false);
        commentToDelete.setDeactivationReason(ECommentDeactivationReason.AUTHOR_REQUEST);
        commentRepository.save(commentToDelete);
    }

    public void insertComment(CommentDTO commentDTO){
        if(commentDTO.description().isBlank()){
            throw new InvalidDataException("Por favor ingrese una descripción para su comentario.");
        }

        if(commentDTO.score() < 0 || commentDTO.score() > 5){
            throw new InvalidDataException("La puntuación debe estar entre 0 y 5.");
        }

        Integer currentUserId = consumerService.getLoggedConsumerId(); //Comentamos siempre con el id de quien esta loggeado

        Comment commentToInsert = new Comment(
                null,
                consumerService.findById(currentUserId),
                spaceService.findById(commentDTO.idSpace()),
                commentDTO.description(),
                commentDTO.score(),
                true,
                null,
                java.time.LocalDateTime.now());

        commentRepository.save(commentToInsert);
    }


    public void modifyComment(Integer id, CommentDTO commentDTO){
        if(!commentRepository.existsById(id)){
            throw new IdNotFoundException("el comentario", id);
        }

        if(commentDTO.description().isBlank()){
            throw new InvalidDataException("Por favor ingrese una descripción para su comentario.");
        }

        Comment existingComment = commentRepository.findById(id)
                .orElseThrow(() -> new IdNotFoundException("el comentario", id));

        if (!Boolean.TRUE.equals(existingComment.getIsActive())) {
            throw new InvalidDataException("No se puede modificar un comentario que no está activo.");
        }

        if((commentDTO.score()*2) % 1 != 0){
            throw new InvalidDataException("La puntuación ingresada no es válida.");
        }

        Integer currentUserId = consumerService.getLoggedConsumerId();

        Comment commentToInsert = new Comment(
                id,
                consumerService.findById(currentUserId),
                spaceService.findById(commentDTO.idSpace()),
                commentDTO.description(),
                commentDTO.score(),
                true,
                null,
                LocalDateTime.now());

        commentRepository.save(commentToInsert);
    }

    public List<CommentDTO> findAllBySpaceId(Integer spaceId){
        if(!spaceService.existsById(spaceId)){
            throw new IdNotFoundException("el espacio", spaceId);
        }
        List<Comment> commentList = commentRepository.findAllBySpaceIdSpace(spaceId);
        return commentList.stream().map(comment -> CommentMapper.mapToDTO(comment)).toList();
    }

    public List<CommentDTO> findAllByConsumerId(Integer consumerId){
        if(!consumerService.existsById(consumerId)){
            throw new IdNotFoundException("el usuario", consumerId);
        }
        List<Comment> commentList = commentRepository.findAllByConsumerIdConsumer(consumerId);
        return commentList.stream().map(comment -> CommentMapper.mapToDTO(comment)).toList();
    }

    public List<CommentDTO> findDeactivatedCommentsBySpaceId(Integer spaceId){
        validateSpaceExists(spaceId);
        List<Comment> commentList = commentRepository.findDeactivatedCommentsForSpace(spaceId);
        return commentList.stream().map(CommentMapper::mapToDTO).toList();
    }

    public List<CommentDTO> findDeactivatedCommentsByConsumerId(Integer consumerId){
        if(!consumerService.existsById(consumerId)){
            throw new IdNotFoundException("el usuario", consumerId);
        }
        List<Comment> commentList = commentRepository.findDeactivatedCommentsForConsumer(consumerId);
        return commentList.stream().map(CommentMapper::mapToDTO).toList();
    }

    public List<CommentDTO> filterByScoreASC() {
        return commentRepository.findAllActiveOrderByScoreAsc()
                .stream()
                .map(CommentMapper::mapToDTO)
                .toList();
    }

    public List<CommentDTO> filterByScoreDESC() {
        return commentRepository.findAllActiveOrderByScoreDesc()
                .stream()
                .map(CommentMapper::mapToDTO)
                .toList();
    }

    public void consumerInsertCommentOnSpace(CommentDTO commentDTO){
        Integer loggedConsumerId = consumerService.getLoggedConsumerId();
        //Averiguamos las reservas del consumer loggeado
        List<Reservation> reservationsForConsumer = reservationService.findByIdConsumer(loggedConsumerId);
        //Filtramos para que queden solo reservas activas, que ademas esten COMPLETED o CONFIRMED
        reservationsForConsumer = reservationsForConsumer.stream()
                .filter(reservation -> reservation.getIsActive() &&
                        (reservation.getStatus().equals(ReservationStatus.COMPLETED) || reservation.getStatus().equals(ReservationStatus.CONFIRMED))).toList();

        //Verificamos que alguna de las reservas fue sobre el espacio que queremos comentar
        if(!reservationsForConsumer.stream().anyMatch(reservation -> reservation.getSpace().getIdSpace().equals(commentDTO.idSpace()))){
            throw new InvalidDataException("Solo puede comentar espacios que haya reservado y cuya reserva se haya completado.");
        }

        insertComment(commentDTO);
    }

    public void consumerModifyCommentOnSpace(Integer id,CommentDTO commentDTO){
        Comment existingComment = commentRepository.findById(id)
                .orElseThrow(() -> new IdNotFoundException("el comentario", id));
        if(!existingComment.getConsumer().getIdConsumer().equals(consumerService.getLoggedConsumerId())){
            throw new AccessDeniedException("No se puede modificar un comentario que no es propio");
        }

        modifyComment(id,commentDTO);
    }

    public boolean hasCommentOnSpace(Integer spaceId) {
        validateSpaceExists(spaceId);
        Integer consumerId = consumerService.getLoggedConsumerId();

        return commentRepository.existsByConsumer_IdConsumerAndSpace_IdSpace(
                consumerId, spaceId
        );
    }

    public boolean canCommentOnSpace(Integer spaceId) {
        validateSpaceExists(spaceId);
        Integer consumerId = consumerService.getLoggedConsumerId();

        return reservationRepository.existsActiveReservationByConsumerAndSpaceAndStatusIn(
                consumerId,
                spaceId,
                List.of(ReservationStatus.COMPLETED, ReservationStatus.CONFIRMED)
        );
    }

    private void validateSpaceExists(Integer spaceId) {
        if (!spaceService.existsById(spaceId)) {
            throw new IdNotFoundException(
                    "No se encontró el espacio: ", spaceId
            );
        }
    }
}