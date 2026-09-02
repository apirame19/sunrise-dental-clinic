package lk.sunrisedental.model;

import java.time.LocalTime;

/**
 * One slot on a dentist's day, and what is in it.
 *
 * <p>Derived rather than stored. The clinic has no "slots" table and should not have one: a slot is
 * simply a division of the opening hours, and storing them would mean writing rows for every dentist
 * for every future day and keeping them in step with any change to opening hours.</p>
 *
 * <p>A slot is unavailable if <em>any</em> appointment overlaps it, not merely one that starts on
 * it. That is the same interval comparison used by {@code fn_is_dentist_available}, so what the
 * availability screen shows and what the booking form accepts cannot disagree.</p>
 *
 * @param startTime      when the slot begins
 * @param endTime        when the slot ends
 * @param available      {@code true} if nothing occupies it
 * @param appointmentNo  the appointment filling it, or {@code null} when free
 * @param patientName    who is booked into it, or {@code null} when free
 * @param treatmentName  what they are booked for, or {@code null} when free
 */
public record AvailabilitySlot(LocalTime startTime,
                               LocalTime endTime,
                               boolean available,
                               String appointmentNo,
                               String patientName,
                               String treatmentName) {

    /**
     * @param startTime when the slot begins
     * @param endTime   when the slot ends
     * @return a free slot
     */
    public static AvailabilitySlot free(LocalTime startTime, LocalTime endTime) {
        return new AvailabilitySlot(startTime, endTime, true, null, null, null);
    }

    /**
     * @param startTime   when the slot begins
     * @param endTime     when the slot ends
     * @param appointment the appointment occupying it
     * @return an occupied slot carrying enough detail for the schedule view
     */
    public static AvailabilitySlot taken(LocalTime startTime, LocalTime endTime,
                                         Appointment appointment) {
        return new AvailabilitySlot(startTime, endTime, false,
                appointment.getAppointmentNo(),
                appointment.getPatient() == null ? null : appointment.getPatient().getPatientName(),
                appointment.getTreatment() == null ? null : appointment.getTreatment().getTreatmentName());
    }
}
