package com.utn.space.venueaapi.service;

import com.mercadopago.exceptions.MPException;
import com.mercadopago.exceptions.MPApiException;
import com.utn.space.venueaapi.model.Reservation;

public interface IPaymentService {
    String createPreference(Reservation reservation) throws MPException, MPApiException;
    void processNotification(Long paymentId);
    void processMockNotification(Long paymentId, Integer reservationId);
}