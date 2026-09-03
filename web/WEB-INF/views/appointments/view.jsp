<%--
    One appointment in full, and the controls that move it through its lifecycle.

    The status buttons are generated from AppointmentStatus.allowedTransitions() rather than being
    hard-coded, so a terminal appointment simply offers none. That is the same rule the service
    enforces and the same rule the database trigger enforces - a cancelled visit can never be
    flipped to completed and then billed, which would put income in the reports for a visit that
    never happened.

    Cancelling asks for a reason in the same form, because the service refuses a cancellation
    without one. Asking for it afterwards, in a second step, would mean a cancellation that failed
    for a reason the user could not see.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Appointment ${appointment.appointmentNo}"
        subtitle="${appointment.appointmentDate} at ${appointment.appointmentTime}">

    <div class="two-column">

        <section class="panel-card">
            <header class="card-head">
                <h2>Details</h2>
                <t:statusPill status="${appointment.status}"/>
            </header>

            <dl class="detail-grid">
                <div>
                    <dt>Patient</dt>
                    <dd>
                        <a href="${pageContext.request.contextPath}/app/patients/view?id=${appointment.patient.patientId}">
                            <c:out value="${appointment.patient.patientName}"/>
                        </a>
                    </dd>
                </div>
                <div>
                    <dt>Contact</dt>
                    <dd><c:out value="${appointment.patient.contactNumber}"/></dd>
                </div>
                <div>
                    <dt>Address</dt>
                    <dd><c:out value="${appointment.patient.address}"/></dd>
                </div>
                <div>
                    <dt>Dentist</dt>
                    <dd>
                        <a href="${pageContext.request.contextPath}/app/dentists/schedule?id=${appointment.dentist.dentistId}&amp;date=${appointment.appointmentDate}">
                            <c:out value="${appointment.dentist.displayName}"/>
                        </a>
                    </dd>
                </div>
                <div>
                    <dt>Treatment</dt>
                    <dd>
                        <c:out value="${appointment.treatment.treatmentName}"/>
                        <span class="muted">(${appointment.treatment.durationMinutes} minutes)</span>
                    </dd>
                </div>
                <div>
                    <dt>Slot</dt>
                    <dd>${appointment.appointmentTime} &ndash; ${appointment.endTime}</dd>
                </div>
                <div>
                    <dt>Booked by</dt>
                    <dd><c:out value="${appointment.createdByName}"/></dd>
                </div>
                <c:if test="${not empty appointment.notes}">
                    <div class="span-2">
                        <dt>Notes</dt>
                        <dd><c:out value="${appointment.notes}"/></dd>
                    </div>
                </c:if>
                <c:if test="${not empty appointment.cancelReason}">
                    <div class="span-2">
                        <dt>Reason for cancellation</dt>
                        <dd><c:out value="${appointment.cancelReason}"/></dd>
                    </div>
                </c:if>
            </dl>
        </section>

        <div class="stack">

            <section class="panel-card">
                <header class="card-head"><h2>Record the outcome</h2></header>

                <c:choose>
                    <c:when test="${empty allowedTransitions}">
                        <p class="empty">
                            This appointment is <c:out value="${appointment.status.label}"/> and can
                            no longer be changed. A terminal status is final, so that a cancelled
                            visit can never be billed as if it had happened.
                        </p>
                    </c:when>
                    <c:otherwise>
                        <form method="post"
                              action="${pageContext.request.contextPath}/app/appointments/status"
                              class="stacked-form">
                            <input type="hidden" name="csrfToken" value="${csrfToken}">
                            <input type="hidden" name="no" value="${appointment.appointmentNo}">

                            <div class="field">
                                <label for="status">New status</label>
                                <select id="status" name="status" required>
                                    <c:forEach var="target" items="${allowedTransitions}">
                                        <option value="${target}"><c:out value="${target.label}"/></option>
                                    </c:forEach>
                                </select>
                            </div>

                            <div class="field">
                                <label for="reason">
                                    Reason <span class="optional">(required when cancelling)</span>
                                </label>
                                <input type="text" id="reason" name="reason" maxlength="255"
                                       aria-describedby="reason-hint">
                                <p class="hint" id="reason-hint">
                                    Recorded against the appointment so the clinic can see why
                                    slots were given up.
                                </p>
                            </div>

                            <button type="submit" class="button primary">Update status</button>
                        </form>
                    </c:otherwise>
                </c:choose>
            </section>

            <section class="panel-card">
                <header class="card-head"><h2>Billing</h2></header>

                <c:choose>
                    <c:when test="${not empty bill}">
                        <p>
                            Bill <strong><c:out value="${bill.billNo}"/></strong> was issued for
                            <c:out value="${clinic.currencyCode}"/>
                            <fmt:formatNumber value="${bill.totalAmount}" minFractionDigits="2"
                                              maxFractionDigits="2"/>.
                        </p>
                        <div class="actions">
                            <a class="button primary"
                               href="${pageContext.request.contextPath}/app/billing/receipt?billNo=${bill.billNo}">
                                View the receipt
                            </a>
                        </div>
                    </c:when>

                    <c:when test="${appointment.status.billable}">
                        <p>This visit is completed and has not been billed yet.</p>
                        <div class="actions">
                            <a class="button primary"
                               href="${pageContext.request.contextPath}/app/billing?no=${appointment.appointmentNo}">
                                Price and issue a bill
                            </a>
                        </div>
                    </c:when>

                    <c:otherwise>
                        <p class="empty">
                            Only a completed visit can be billed. Record the outcome first &mdash;
                            a booking is not income.
                        </p>
                    </c:otherwise>
                </c:choose>
            </section>
        </div>
    </div>

</t:page>
