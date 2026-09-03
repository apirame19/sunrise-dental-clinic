<%--
    The patient's own appointment request form.

    Four choices only: dentist, treatment, day and time. The patient's name, address and contact
    number are NOT on this form and are not accepted from it - the server fills them in from the
    record bound to the signed-in account. There is no patient id, no appointment number and no
    status field anywhere below, because none of those is something a patient may choose.

    The form submits twice by design. The first submission is the "Show available times" button,
    which is an ordinary GET carrying the chosen dentist and day; the server then renders the same
    page with that dentist's real slots. The second is the request itself. This is what makes a
    live availability picker possible with no JavaScript at all (constraint C-04).

    The slots shown are the same DentistService.availability calculation the staff schedule screen
    uses, so a patient is offered exactly the times the clinic believes are free. What a patient
    sees of a taken slot is only that it is taken - never who is in it.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Request an appointment"
        subtitle="Choose a dentist and a time. The clinic will confirm it.">

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <section class="card">
        <h2>1. Choose a dentist and a day</h2>

        <%-- A GET, because it changes nothing: it only asks the server which times are free. --%>
        <form method="get" action="${pageContext.request.contextPath}/app/my/request"
              class="stacked-form">

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
                    <label for="date">Day</label>
                    <input type="date" id="date" name="date" required
                           value="<c:out value='${form.appointmentDate}'/>"
                           min="${earliestDate}" max="${latestDate}"
                           aria-describedby="date-hint">
                    <p class="hint" id="date-hint">
                        The clinic is closed on Sundays. Bookings can be made up to
                        ${clinic.maxBookingDaysAhead} days ahead.
                    </p>
                </div>
            </div>

         <%-- BUG FIX: Only carry treatmentId if it has a value. When the patient first clicks
     "Show available times" before selecting a treatment, passing an empty treatmentId
     parameter causes a 500 error. Since the treatment is selected in the second form
     (after viewing available times), it's safe to omit it here if not yet chosen. --%>
<c:if test="${not empty form.treatmentId}">
    <input type="hidden" name="treatmentId" value="<c:out value='${form.treatmentId}'/>">
</c:if>
            <div class="actions">
                <button type="submit" class="button">Show available times</button>
            </div>
        </form>
    </section>

    <section class="card">
        <h2>2. Choose a treatment and a time</h2>

        <c:choose>
            <c:when test="${empty slots}">
                <p class="empty">
                    Choose a dentist and a day above, then select &ldquo;Show available
                    times&rdquo;. The times offered are the ones that dentist actually has free.
                </p>
            </c:when>
            <c:otherwise>
                <form method="post" action="${pageContext.request.contextPath}/app/my/request"
                      class="stacked-form">
                    <%-- CSRF: this POST changes clinic data, so it carries the session token. --%>
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <input type="hidden" name="dentistId" value="<c:out value='${form.dentistId}'/>">
                    <input type="hidden" name="appointmentDate" value="${slotDate}">

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
                            A longer treatment needs more of the dentist's time, so some start
                            times may still be refused when you submit.
                        </p>
                        <t:fieldError validation="${validation}" field="treatmentId"/>
                    </div>

                    <fieldset class="field">
                        <legend>Time on <c:out value="${slotDate}"/></legend>

                        <div class="slot-grid">
                            <c:forEach var="slot" items="${slots}">
                                <c:choose>
                                    <c:when test="${slot.available}">
                                        <label class="slot-choice">
                                            <input type="radio" name="appointmentTime"
                                                   value="${slot.startTime}" required
                                                   ${form.appointmentTime eq slot.startTime ? 'checked' : ''}>
                                            <span><c:out value="${slot.startTime}"/></span>
                                        </label>
                                    </c:when>
                                    <c:otherwise>
                                        <%-- Taken slots are shown, not hidden: a patient can see
                                             the day is busy rather than wondering why the times
                                             jump. Nothing about who occupies it is rendered. --%>
                                        <span class="slot-choice taken"
                                              aria-label="<c:out value='${slot.startTime}'/> is not available">
                                            <span><c:out value="${slot.startTime}"/></span>
                                        </span>
                                    </c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </div>

                        <t:fieldError validation="${validation}" field="appointmentTime"/>
                        <t:fieldError validation="${validation}" field="appointmentDate"/>
                    </fieldset>

                    <div class="field">
                        <label for="notes">Anything the clinic should know
                            <span class="optional">(optional)</span></label>
                        <textarea id="notes" name="notes" rows="3"
                                  maxlength="500"><c:out value="${form.notes}"/></textarea>
                    </div>

                    <p class="hint">
                        Submitting this sends a <strong>request</strong>. Your appointment is not
                        confirmed until the clinic has approved it, and you will see the status
                        change on your appointments page.
                    </p>

                    <div class="actions">
                        <button type="submit" class="button primary">Send request</button>
                        <a class="button ghost"
                           href="${pageContext.request.contextPath}/app/my/appointments">Cancel</a>
                    </div>
                </form>
            </c:otherwise>
        </c:choose>
    </section>

</t:page>
