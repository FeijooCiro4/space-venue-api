package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.Location;
import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.repository.LocationRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@AllArgsConstructor
@Service
public class LocationService {
    @Autowired
    LocationRepository locationRepository;

    private static final BigDecimal EARTH_RADIOUS = new BigDecimal("6371.0");

    public Location findById(Integer id){
        return locationRepository.findById(id).orElseThrow(()-> new IdNotFoundException("No se encontro la ubicacion: ", id));
    }

    public Location findByLongitudeAndLatitude(BigDecimal longitude, BigDecimal latitude){
        return locationRepository.findLocationByLongitudeAndLatitude(longitude, latitude).orElseThrow(
                () -> new InvalidDataException("No se ha encontrado la ubicación en la base de datos bajo las coordenadas: lt=" + latitude + " lg=" + longitude));
    }

    public boolean existsById(Integer id){
        return locationRepository.existsById(id);
    }

    //calcula la sistancia entre dos puntos
    private double calculateDistance(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2){
        double dlat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dlon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());

        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                   Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue())) *
                   Math.sin(dlon / 2) * Math.sin(dlon / 2);

        double c = 2* Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIOUS.doubleValue() * c;
    }

    // radiousKm es la distancia del usuario desde la que vamos a considerar los espacios para su filtrado
    public Boolean isSpaceNearby(BigDecimal userLat, BigDecimal userLng, BigDecimal radiousKm,Space space){
        return calculateDistance(userLat,userLng,space.getLocation().getLatitude(),space.getLocation().getLongitude()) <= radiousKm.doubleValue();
    }
}
