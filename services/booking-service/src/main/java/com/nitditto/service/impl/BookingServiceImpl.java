package com.nitditto.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nitditto.service.BookingService;

@Service
@Transactional(readOnly = true)
public class BookingService implements BookingService {
}
