package lk.sunrisedental.controller.api;

import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AvailabilitySlot;
import lk.sunrisedental.model.Bill;
import lk.sunrisedental.model.BillLine;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.model.PatientHistory;
import lk.sunrisedental.model.Reminder;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.model.User;
import lk.sunrisedental.patterns.composite.ReportComponent;
import lk.sunrisedental.util.JsonWriter;

import java.util.Collection;
import java.util.function.Function;

/**
 * Renders domain objects as JSON.
 *
 * <p>One place, so that an appointment looks the same whichever endpoint returned it. Scattering
 * these conversions through the endpoints would produce a search result and a day list whose
 * appointments carried subtly different member names, which is exactly the sort of thing a
 * consumer discovers in production.</p>
 *
 * <p><strong>Nothing here exposes a credential.</strong> {@link #user(User)} omits the password
 * hash, the salt and the iteration count outright - not because they are secret in a useful sense,
 * but because a field that is never written cannot later be written by accident.</p>
 *
 * <p>Dates and times are rendered as ISO strings ({@code 2026-08-11}, {@code 14:30}) and money as
 * an unquoted decimal number. ISO because it sorts and parses everywhere; unquoted decimal because
 * {@code BigDecimal.toPlainString} never produces exponent notation, so the value a consumer reads
 * is the value the clinic charged.</p>
 */
public final class JsonModels {

    private JsonModels() {
    }

    // ------------------------------------------------------------------ collections

    /**
     * @param items   the objects to render
     * @param mapper  how to render one
     * @return a JSON array
     */
    public static <T> JsonWriter.JsonArray arrayOf(Collection<T> items,
                                                   Function<T, JsonWriter.JsonValue> mapper) {
        JsonWriter.JsonArray array = JsonWriter.array();
        items.forEach(item -> array.add(mapper.apply(item)));
        return array;
    }

    // ------------------------------------------------------------------ people

    /** @return a patient; no clinical detail, only identity and contact. */
    public static JsonWriter.JsonObject patient(Patient patient) {
        if (patient == null) {
            return null;
        }
        return JsonWriter.object()
                .add("patientId", patient.getPatientId())
                .add("patientName", patient.getPatientName())
                .add("address", patient.getAddress())
                .add("contactNumber", patient.getContactNumber())
                .add("email", patient.getEmail());
    }

    /** @return a dentist. */
    public static JsonWriter.JsonObject dentist(Dentist dentist) {
        if (dentist == null) {
            return null;
        }
        return JsonWriter.object()
                .add("dentistId", dentist.getDentistId())
                .add("dentistName", dentist.getDentistName())
                .add("specialization", dentist.getSpecialization())
                .add("licenseNo", dentist.getLicenseNo())
                .add("contactNumber", dentist.getContactNumber())
                .add("active", dentist.isActive());
    }

    /** @return a staff account. Credential fields are deliberately absent. */
    public static JsonWriter.JsonObject user(User user) {
        if (user == null) {
            return null;
        }
        return JsonWriter.object()
                .add("userId", user.getUserId())
                .add("username", user.getUsername())
                .add("fullName", user.getFullName())
                .add("role", user.getRole() == null ? null : user.getRole().name())
                .add("active", user.isActive())
                .add("lastLoginAt", user.getLastLoginAt() == null
                        ? null : user.getLastLoginAt().toString());
    }

    // ------------------------------------------------------------------ catalogue

    /** @return a treatment and its list price. */
    public static JsonWriter.JsonObject treatment(Treatment treatment) {
        if (treatment == null) {
            return null;
        }
        return JsonWriter.object()
                .add("treatmentId", treatment.getTreatmentId())
                .add("treatmentCode", treatment.getTreatmentCode())
                .add("treatmentName", treatment.getTreatmentName())
                .add("description", treatment.getDescription())
                .add("baseCost", treatment.getBaseCost())
                .add("durationMinutes", treatment.getDurationMinutes())
                .add("taxable", treatment.isTaxable())
                .add("active", treatment.isActive());
    }

    // ------------------------------------------------------------------ appointments

    /**
     * @return an appointment with its patient, dentist and treatment nested.
     *         Nested rather than as bare ids, because every consumer of this needs the names and
     *         would otherwise have to issue three follow-up calls per row.
     */
    public static JsonWriter.JsonObject appointment(Appointment appointment) {
        if (appointment == null) {
            return null;
        }
        return JsonWriter.object()
                .add("appointmentId", appointment.getAppointmentId())
                .add("appointmentNo", appointment.getAppointmentNo())
                .add("appointmentDate", appointment.getAppointmentDate() == null
                        ? null : appointment.getAppointmentDate().toString())
                .add("appointmentTime", appointment.getAppointmentTime() == null
                        ? null : appointment.getAppointmentTime().toString())
                .add("endTime", appointment.getEndTime() == null
                        ? null : appointment.getEndTime().toString())
                .add("status", appointment.getStatus() == null
                        ? null : appointment.getStatus().name())
                .add("statusLabel", appointment.getStatus() == null
                        ? null : appointment.getStatus().getLabel())
                .add("billable", appointment.isBillable())
                .add("billed", appointment.isBilled())
                .add("billNo", appointment.getBillNo())
                .add("notes", appointment.getNotes())
                .add("cancelReason", appointment.getCancelReason())
                .add("createdBy", appointment.getCreatedByName())
                .add("patient", patient(appointment.getPatient()))
                .add("dentist", dentist(appointment.getDentist()))
                .add("treatment", treatment(appointment.getTreatment()));
    }

    /** @return one slot on a dentist's day, free or taken. */
    public static JsonWriter.JsonObject slot(AvailabilitySlot slot) {
        return JsonWriter.object()
                .add("startTime", slot.startTime() == null ? null : slot.startTime().toString())
                .add("endTime", slot.endTime() == null ? null : slot.endTime().toString())
                .add("available", slot.available())
                .add("appointmentNo", slot.appointmentNo())
                .add("patientName", slot.patientName())
                .add("treatmentName", slot.treatmentName());
    }

    // ------------------------------------------------------------------ billing

    /**
     * @return a bill with its itemised lines.
     *         The lines are included rather than being a separate call, because a total without
     *         its breakdown is precisely what the clinic complained about.
     */
    public static JsonWriter.JsonObject bill(Bill bill) {
        if (bill == null) {
            return null;
        }
        return JsonWriter.object()
                .add("billId", bill.getBillId())
                .add("billNo", bill.getBillNo())
                .add("appointmentNo", bill.getAppointmentNo())
                .add("treatmentCost", bill.getTreatmentCost())
                .add("consultationFee", bill.getConsultationFee())
                .add("discountAmount", bill.getDiscountAmount())
                .add("taxAmount", bill.getTaxAmount())
                .add("totalAmount", bill.getTotalAmount())
                .add("internallyConsistent", bill.isInternallyConsistent())
                .add("generatedBy", bill.getGeneratedByName())
                .add("generatedAt", bill.getGeneratedAt() == null
                        ? null : bill.getGeneratedAt().toString())
                .add("lines", arrayOf(bill.getLines(), JsonModels::billLine));
    }

    /** @return one itemised line; the amount is signed, so the lines sum to the total. */
    public static JsonWriter.JsonObject billLine(BillLine line) {
        return JsonWriter.object()
                .add("lineNo", line.getLineNo())
                .add("lineType", line.getLineType().name())
                .add("description", line.getDescription())
                .add("amount", line.getAmount());
    }

    // ------------------------------------------------------------------ history and reminders

    /** @return a patient's complete record: the patient, every visit, every bill and the totals. */
    public static JsonWriter.JsonObject patientHistory(PatientHistory history) {
        return JsonWriter.object()
                .add("patient", patient(history.getPatient()))
                .add("visitCount", history.getVisitCount())
                .add("completedCount", history.getCompletedCount())
                .add("cancelledCount", history.getCancelledCount())
                .add("noShowCount", history.getNoShowCount())
                .add("upcomingCount", history.getUpcomingCount())
                .add("totalBilled", history.getTotalBilled())
                .add("appointments", arrayOf(history.getAppointments(), JsonModels::appointment))
                .add("bills", arrayOf(history.getBills(), JsonModels::bill));
    }

    /** @return one reminder, with the appointment it refers to nested. */
    public static JsonWriter.JsonObject reminder(Reminder reminder) {
        return JsonWriter.object()
                .add("type", reminder.getType().name())
                .add("label", reminder.getType().getLabel())
                .add("priority", reminder.getType().getPriority())
                .add("actionHint", reminder.getType().getActionHint())
                .add("overdue", reminder.isOverdue())
                .add("timing", reminder.getTimingDescription())
                .add("appointment", appointment(reminder.getAppointment()));
    }

    // ------------------------------------------------------------------ reports

    /**
     * COMPOSITE, serialised.
     *
     * <p>Recurses over the tree exactly as the JSP fragment does, so the JSON structure mirrors
     * what the screen shows - including the subtotal at every level, which is derived from the
     * children rather than carried alongside them.</p>
     *
     * @param node any node of a report tree
     * @return that node and everything beneath it
     */
    public static JsonWriter.JsonObject report(ReportComponent node) {
        JsonWriter.JsonObject json = JsonWriter.object()
                .add("title", node.getTitle())
                .add("subtitle", node.getSubtitle())
                .add("total", node.getTotal())
                .add("count", node.getCount())
                .add("leaf", node.isLeaf())
                .add("depth", node.getDepth());

        if (node instanceof lk.sunrisedental.patterns.composite.ReportLine line) {
            JsonWriter.JsonObject columns = JsonWriter.object();
            line.getColumns().forEach(columns::add);
            json.add("columns", columns);
        } else {
            json.add("children", arrayOf(node.getChildren(), JsonModels::report));
        }
        return json;
    }
}
