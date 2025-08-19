package com.example.Pointage_Cleanic.services;

import java.time.format.DateTimeParseException;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.PointageRequest;
import com.example.Pointage_Cleanic.repositories.PointageRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PointageRequestService {

    private final PointageRequestRepository pointageRequestRepository;
    private final EmployeServices employeServices ;
    private final PointageServices pointageServices;





}
