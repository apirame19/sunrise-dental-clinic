package lk.sunrisedental.service;

import lk.sunrisedental.dao.AppointmentDAO;
import lk.sunrisedental.dao.DentistDAO;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AvailabilitySlot;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;
import lk.sunrisedental.validation.ValidationResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The practice roster: who works here, when they are free, and what they are booked for.
 *
 * <p>{@link #availability(int, LocalDate)} is the reason this service exists rather than the
 * servlet simply listing appointments. It divides the clinic's opening hours into slots and marks
 * each one against the dentist's actual bookings, using the same interval-overlap rule as
 * {@code fn_is_dentist_available}: a slot is taken if any appointment overlaps it, not merely if one
 * starts on it. A 90-minute root canal at 10:00 therefore blocks 10:30 and 11:00 on this screen, as
 * it does on the booking form.</p>
 */
public class DentistService {

    private final DentistDAO dentistDAO;
    private final AppointmentDAO appointmentDAO;
    private final ConfigurationManager configuration;

    public DentistService(DentistDAO dentistDAO, AppointmentDAO appointmentDAO,
                          ConfigurationManager configuration) {
        this.dentistDAO = dentistDAO;
        this.appointmentDAO = appointmentDAO;
        this.configuration = configuration;
    }

    /** @return every dentist, active ones first, for the management screen. */
    public List<Dentist> findAll() {
        return dentistDAO.findAll();
    }

    /** @return dentists currently taking appointments, for booking dropdowns. */
    public List<Dentist> findAllActive() {
        return dentistDAO.findAllActive();
    }

    /**
     * @param dentistId the dentist
     * @return the dentist
     * @throws BusinessRuleException if no such dentist exists
     */
    public Dentist findById(int dentistId) {
        return dentistDAO.findById(dentistId)
                .orElseThrow(() -> new BusinessRuleException("DENTIST_NOT_FOUND",
                        "No dentist record was found."));
    }

    /** @return the number of dentists currently practising. */
    public long countActive() {
        return dentistDAO.countActive();
    }

    /**
     * Adds a dentist to the practice.
     *
     * @param dentist the details to store
     * @return the stored dentist, carrying its generated id
     * @throws ValidationException if any field is unacceptable
     */
    public Dentist add(Dentist dentist) {
        ValidationResult result = validate(dentist, null);
        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
        dentist.setActive(true);
        dentist.setDentistId(dentistDAO.insert(dentist));
        return dentist;
    }

    /**
     * Updates a dentist's details.
     *
     * @param dentist the dentist carrying the new values and an existing id
     * @return the updated dentist
     */
    public Dentist update(Dentist dentist) {
        Dentist existing = findById(dentist.getDentistId());

        ValidationResult result = validate(dentist, existing.getDentistId());
        if (result.hasErrors()) {
            throw new ValidationException(result);
        }

        existing.setDentistName(dentist.getDentistName().trim());
        existing.setSpecialization(dentist.getSpecialization().trim());
        existing.setLicenseNo(dentist.getLicenseNo().trim());
        existing.setContactNumber(trimToNull(dentist.getContactNumber()));

        if (!dentistDAO.update(existing)) {
            throw new BusinessRuleException("DENTIST_UPDATE_FAILED",
                    "The dentist record could not be updated.");
        }
        return existing;
    }

    /**
     * Withdraws a dentist from the booking list, or reinstates them.
     *
     * <p>Deactivation is refused while the dentist still has appointments ahead of them: those
     * patients have been promised a specific clinician, and silently leaving the bookings in place
     * with the dentist off the roster is how a clinic ends up with a full waiting room and nobody to
     * see them. The appointments must be cancelled or reassigned first.</p>
     *
     * @param dentistId the dentist
     * @param active    {@code true} to reinstate, {@code false} to withdraw
     * @param asOf      today's date
     * @return the updated dentist
     */
    public Dentist setActive(int dentistId, boolean active, LocalDate asOf) {
        Dentist existing = findById(dentistId);

        if (!active && existing.isActive()) {
            long upcoming = appointmentDAO.findByDateRange(asOf, asOf.plusYears(2)).stream()
                    .filter(appointment -> appointment.getDentist().getDentistId() == dentistId)
                    .filter(appointment -> appointment.getStatus().occupiesSlot())
                    .filter(appointment -> !appointment.getAppointmentDate().isBefore(asOf))
                    .count();

            if (upcoming > 0) {
                throw new BusinessRuleException("DENTIST_HAS_APPOINTMENTS",
                        existing.getDentistName() + " still has " + upcoming
                        + (upcoming == 1 ? " appointment" : " appointments")
                        + " booked. Cancel or reassign them before withdrawing this dentist.");
            }
        }

        if (existing.isActive() == active) {
            return existing;
        }
        if (!dentistDAO.setActive(dentistId, active)) {
            throw new BusinessRuleException("DENTIST_UPDATE_FAILED",
                    "The dentist record could not be updated.");
        }
        existing.setActive(active);
        return existing;
    }

    /**
     * @param dentistId the dentist
     * @param date      the day
     * @return that dentist's appointments for the day, ordered by time
     */
    public List<Appointment> schedule(int dentistId, LocalDate date) {
        return appointmentDAO.findByDentistAndDate(dentistId, date);
    }

    /**
     * Works out which slots a dentist has free on a day.
     *
     * @param dentistId the dentist
     * @param date      the day
     * @return every slot in the clinic's opening hours, marked free or taken, in time order
     */
    public List<AvailabilitySlot> availability(int dentistId, LocalDate date) {
        List<Appointment> booked = schedule(dentistId, date).stream()
                .filter(appointment -> appointment.getStatus().occupiesSlot())
                .toList();

        LocalTime open = configuration.getOpeningTime();
        LocalTime close = configuration.getClosingTime();
        int slotMinutes = Math.max(5, configuration.getSlotMinutes());

        List<AvailabilitySlot> slots = new ArrayList<>();
        LocalTime cursor = open;

        while (!cursor.plusMinutes(slotMinutes).isAfter(close)) {
            LocalTime start = cursor;
            LocalTime end = cursor.plusMinutes(slotMinutes);

            slots.add(overlapping(booked, start, end)
                    .map(appointment -> AvailabilitySlot.taken(start, end, appointment))
                    .orElseGet(() -> AvailabilitySlot.free(start, end)));

            cursor = end;
        }
        return slots;
    }

    /**
     * @return the appointment covering any part of {@code [start, end)}, if there is one.
     *         Two intervals overlap when each begins before the other ends - the same test the
     *         database function applies.
     */
    private static Optional<Appointment> overlapping(List<Appointment> booked,
                                                     LocalTime start, LocalTime end) {
        return booked.stream()
                .filter(appointment -> {
                    LocalTime bookedStart = appointment.getAppointmentTime();
                    LocalTime bookedEnd = appointment.getEndTime();
                    if (bookedStart == null || bookedEnd == null) {
                        return false;
                    }
                    return start.isBefore(bookedEnd) && bookedStart.isBefore(end);
                })
                .findFirst();
    }

    private ValidationResult validate(Dentist dentist, Integer existingId) {
        ValidationResult result = new ValidationResult();

        String name = trimToNull(dentist.getDentistName());
        if (name == null || name.length() < 2) {
            result.addError("dentistName", "Please enter the dentist's full name.");
        } else if (name.length() > 100) {
            result.addError("dentistName", "The name may be at most 100 characters.");
        }

        String specialisation = trimToNull(dentist.getSpecialization());
        if (specialisation == null) {
            result.addError("specialization",
                    "Please enter a specialisation, for example General Dentistry.");
        } else if (specialisation.length() > 100) {
            result.addError("specialization", "The specialisation may be at most 100 characters.");
        }

        String licence = trimToNull(dentist.getLicenseNo());
        if (licence == null) {
            result.addError("licenseNo", "Please enter the SLMC registration number.");
        } else if (licence.length() > 30) {
            result.addError("licenseNo", "The registration number may be at most 30 characters.");
        } else {
            // uk_dentists_license would catch this, but a constraint violation is not a sentence
            // the front desk can act on.
            dentistDAO.findByLicenseNo(licence)
                    .filter(other -> existingId == null || other.getDentistId() != existingId)
                    .ifPresent(other -> result.addError("licenseNo",
                            "That registration number is already recorded against "
                            + other.getDentistName() + "."));
        }

        String contact = trimToNull(dentist.getContactNumber());
        if (contact != null && contact.length() > 20) {
            result.addError("contactNumber", "The contact number may be at most 20 characters.");
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
