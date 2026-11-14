package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.model.Space;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SpaceCommentScheduler {
    @Autowired
    private SpaceService spaceService;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void actualizarRatingYCount(){
        List<Space> lista = spaceService.findAll();
        for(Space space : lista){
            spaceService.calcularRatingYCount(space);
        }
    }

}
