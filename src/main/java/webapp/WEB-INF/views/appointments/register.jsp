<%--
    The booking form - the screen the clinic spends its day on.

    Every field is repopulated from the submitted AppointmentForm after a rejection, because the
    validation chain reports all the problems at once and a receptionist must be able to fix them
    in one pass. Losing eight fields of typing over one bad telephone number is the behaviour this
    system exists to replace.

    Patient details are typed rather than picked from a list: the front desk is usually on the
    telephone to somebody who may or may not already be on file. The service matches on the
    normalised contact number and reuses the existing record when it finds one, so the same person
    does not end up registered twice.

    No JavaScript. The date and time inputs are native HTML types, so the browser provides its own
    picker and the server validates the result regardless of what the browser allowed.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Register appointment"
        subtitle="Book a patient in. A new patient is put on file automatically.">

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/app/appointments/register"
          class="stacked-form card-form">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <fieldset>
            <legend>Appointment</legend>

            <div class="field">
                <label for="appointmentNo">Appointment number</label>
                <input type="text" id="appointmentNo" name="appointmentNo" required maxlength="20"
                       value="<c:out value='${form.appointmentNo}'/>"
                       aria-describedby="appointmentNo-hint appointmentNo-error"
                       <c:if test="${not empty validation.fieldErrors['appointmentNo']}">aria-invalid="true"</c:if>>
                <p class="hint" id="appointmentNo-hint">
                    Suggested from the clinic's own sequence. Change it if you need to.
                </p>
                <t:fieldError validation="${validation}" field="appointmentNo"/>
            </div>

            <div class="field-row">
                <div class="field">
                    <label for="appointmentDate">Date</label>
                    <input type="date" id="appointmentDate" name="appointmentDate" required
                           value="<c:out value='${form.appointmentDate}'/>"
                           min="${today}"
                           aria-describedby="appointmentDate-hint appointmentDate-error"
                           <c:if test="${not empty validation.fieldErrors['appointmentDate']}">aria-invalid="true"</c:if>>
                    <p class="hint" id="appointmentDate-hint">
                        Up to ${maxDaysAhead} days ahead. The clinic is closed on Sundays.
                    </p>
                    <t:fieldError validation="${validation}" field="appointmentDate"/>
                </div>

                <div class="field">
                    <label for="appointmentTime">Time</label>
                    <input type="time" id="appointmentTime" name="appointmentTime" required
                           value="<c:out value='${form.appointmentTime}'/>"
                           aria-describedby="appointmentTime-hint appointmentTime-error"
                           <c:if test="${not empty validation.fieldErrors['appointmentTime']}">aria-invalid="true"</c:if>>
                    <p class="hint" id="appointmentTime-hint">
                        Between ${clinicOpen} and ${clinicClose}.
                    </p>
                    <t:fieldError validation="${validation}" field="appointmentTime"/>
                </div>
            </div>

            <div class="field-row">
                <div class="field">
                    <label for="dentistId">Dentist</label>
                    <select id="dentistId" name="dentistId" required
                            <c:if test="${not empty validation.fieldErrors['dentistId']}">aria-invalid="true"</c:if>>
                        <option value="">Choose a dentist&hellip;</option>
                        <c:forEach var="dentist" items="${dentists}">
                            <option value="${dentist.dentistId}"
                                    ${form.dentistId eq dentist.dentistId ? 'selected' : ''}>
                                <c:out value="${dentist.displayName}"/>
                            </option>
                        </c:forEach>
                    </select>
                    <t:fieldError validation="${validation}" field="dentistId"/>
                </div>

                <div class="field">
                    <label for="treatmentId">Treatment</label>
                    <select id="treatmentId" name="treatmentId" required
                            aria-describedby="treatmentId-hint treatmentId-error"
                            <c:if test="${not empty validation.fieldErrors['treatmentId']}">aria-invalid="true"</c:if>>
                        <option value="">Choose a treatment&hellip;</option>
                        <c:forEach var="treatment" items="${treatments}">
                            <option value="${treatment.treatmentId}"
                                    ${form.treatmentId eq treatment.treatmentId ? 'selected' : ''}>
                                <c:out value="${treatment.displayName}"/>
                                &mdash; ${treatment.durationMinutes} min
                            </option>
                        </c:forEach>
                    </select>
                    <p class="hint" id="treatmentId-hint">
                        The treatment's duration decides how long the dentist's chair is blocked.
                    </p>
                    <t:fieldError validation="${validation}" field="treatmentId"/>
                </div>
            </div>
        </fieldset>

        <fieldset>
            <legend>Patient</legend>

            <div class="field">
                <label for="patientName">Full name</label>
                <input type="text" id="patientName" name="patientName" required maxlength="100"
                       value="<c:out value='${form.patientName}'/>"
                       autocomplete="off"
                       <c:if test="${not empty validation.fieldErrors['patientName']}">aria-invalid="true"</c:if>>
                <t:fieldError validation="${validation}" field="patientName"/>
            </div>

            <div class="field">
                <label for="address">Address</label>
                <input type="text" id="address" name="address" required maxlength="255"
                       value="<c:out value='${form.address}'/>" autocomplete="off"
                       <c:if test="${not empty validation.fieldErrors['address']}">aria-invalid="true"</c:if>>
                <t:fieldError validation="${validation}" field="address"/>
            </div>

            <div class="field">
                <label for="contactNumber">Contact number</label>
                <input type="tel" id="contactNumber" name="contactNumber" required maxlength="20"
                       value="<c:out value='${form.contactNumber}'/>" autocomplete="off"
                       aria-describedby="contactNumber-hint contactNumber-error"
                       <c:if test="${not empty validation.fieldErrors['contactNumber']}">aria-invalid="true"</c:if>>
                <p class="hint" id="contactNumber-hint">
                    A Sri Lankan mobile or land line. Spaces and dashes are fine; the number is
                    stored in one form so the same patient is recognised next time.
                </p>
                <t:fieldError validation="${validation}" field="contactNumber"/>
            </div>

            <div class="field">
                <label for="notes">Notes <span class="optional">(optional)</span></label>
                <textarea id="notes" name="notes" rows="3" maxlength="500"><c:out value="${form.notes}"/></textarea>
            </div>
        </fieldset>

        <div class="actions">
            <button type="submit" class="button primary">Register appointment</button>
            <a class="button ghost"
               href="${pageContext.request.contextPath}/app/appointments/day">Cancel</a>
        </div>
    </form>

</t:page>
