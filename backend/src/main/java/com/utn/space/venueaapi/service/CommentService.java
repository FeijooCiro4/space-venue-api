package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.Comment;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.ReservationStatus;
import com.utn.space.venueaapi.model.records.CommentDTO;
import com.utn.space.venueaapi.repository.CommentRepository;
import com.utn.space.venueaapi.service.mappers.CommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public List<CommentDTO> findAll(){
        List<Comment> commentList = commentRepository.findAll();
         return commentList.stream().map(CommentMapper::mapToDTO).toList();
    }

    public CommentDTO findById(Integer id){
        return CommentMapper.mapToDTO(commentRepository.findById(id).orElseThrow(()-> new IdNotFoundException("No se encontro el comentario buscado: ", id)));
    }

    public void deleteById(Integer id){
        if(!commentRepository.existsById(id)){
            throw new IdNotFoundException("No se encontro el comentario a eliminar: ", id);
        }
        commentRepository.deleteById(id);
    }

    public void deleteByIdCustomer(Integer id){
        Comment commentToDelete = commentRepository.findById(id).orElseThrow(()-> new IdNotFoundException("No se encontro el comentario a eliminar: ", id));
        //Logica para que un usuario solo elimine comentarios propios
        if(!commentToDelete.getConsumer().getIdConsumer().equals(consumerService.getLoggedConsumerId())){
            throw new InvalidDataException("No se puede elimnar un comentario que no es propio");
        }
        commentRepository.deleteById(id);
    }

    public void insertComment(CommentDTO commentDTO){
        if(commentDTO.description().isBlank()){
            throw new InvalidDataException("Por favor ingrese una descripcion para su comentario");
        }

        if(commentDTO.score() < 0 || commentDTO.score() > 5){
            throw new InvalidDataException("El score debe estar entre 0 y 5");
        }

        Integer currentUserId = consumerService.getLoggedConsumerId(); //Comentamos siempre con el id de quien esta loggeado

        Comment commentToInsert = new Comment(
                null,
                consumerService.findById(currentUserId),
                spaceService.findById(commentDTO.idSpace()),
                commentDTO.description(),
                commentDTO.score(),
                java.time.LocalDateTime.now());

        commentRepository.save(commentToInsert);
    }


    public void modifyComment(Integer id, CommentDTO commentDTO){
        if(!commentRepository.existsById(id)){
            throw new IdNotFoundException("No se encontro el comentario a eliminar: ", id);
        }

        if(commentDTO.description().isBlank()){
            throw new InvalidDataException("Por favor ingrese una descripcion para su comentario");
        }

        if((commentDTO.score()*2) % 1 != 0){
            throw new InvalidDataException("El score ingresado es invalido");
        }

        Integer currentUserId = consumerService.getLoggedConsumerId();

        Comment commentToInsert = new Comment(
                id,
                consumerService.findById(currentUserId),
                spaceService.findById(commentDTO.idSpace()),
                commentDTO.description(),
                commentDTO.score(),
                commentDTO.created_at());

        commentRepository.save(commentToInsert);
    }

    public List<CommentDTO> findAllBySpaceId(Integer spaceId){
        if(!spaceService.existsById(spaceId)){
            throw new IdNotFoundException("No se encontro el espacio del cual se quieren buscar comentarios: ", spaceId);
        }
        List<Comment> commentList = commentRepository.findAllBySpaceIdSpace(spaceId);
        return commentList.stream().map(comment -> CommentMapper.mapToDTO(comment)).toList();
    }

    public List<CommentDTO> findAllByConsumerId(Integer consumerId){
        if(!consumerService.existsById(consumerId)){
            throw new IdNotFoundException("No se encontro el consumidor del cual se quieren buscar comentarios: ", consumerId);
        }
        List<Comment> commentList = commentRepository.findAllByConsumerIdConsumer(consumerId);
        return commentList.stream().map(comment -> CommentMapper.mapToDTO(comment)).toList();
    }
    public List<CommentDTO> filterByScoreASC(){
        List<Comment> commentList =commentRepository.findAllByOrderByScoreAsc();
        return commentList.stream().map(comment -> CommentMapper.mapToDTO(comment)).toList();
    }

    public List<CommentDTO> filterByScoreDESC(){
        List<Comment> commentList =commentRepository.findAllByOrderByScoreDesc();
        return commentList.stream().map(comment -> CommentMapper.mapToDTO(comment)).toList();
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
            throw new InvalidDataException("No puede comentar sobre un espacio que nunca reservo o cuya reserva no completo");
        }

        insertComment(commentDTO);
    }

    public void consumerModifyCommentOnSpace(Integer id,CommentDTO commentDTO){
        if(!commentDTO.idConsumer().equals(consumerService.getLoggedConsumerId())){
            throw new InvalidDataException("No se puede modificar un comentario que no es propio");
        }

        modifyComment(id,commentDTO);
    }
}