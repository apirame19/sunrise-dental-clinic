<%--
    The signed-in patient's own appointments.

    This is the only page a PATIENT may reach besides Help; the authorisation filter refuses that
    role everything else. The record shown is resolved from the session, so there is no id in the
    URL a patient could change to read somebody else's history.

    Deliberately shows less than the staff view of the same patient: no billing breakdown, no
    internal notes, no cancellation reasons recorded by staff.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="My appointments"
        subtitle="Your visits at Sunrise Dental Clinic.">

    <section class="card">
        <h2>My details</h2>
        <dl class="detail-grid">
            <dt>Name</dt>
            <dd><c:out value="${patient.patientName}"/></dd>
            <dt>Contact number</dt>
            <dd><c:out value="${patient.contactNumber}"/></dd>
            <dt>Address</dt>
            <dd><c:out value="${patient.address}"/></dd>
            <c:if test="${not empty patient.email}">
                <dt>Email</dt>
                <dd><c:out value="${patient.email}"/></dd>
            </c:if>
        </dl>
        <p class="hint">
            To change any of these, please contact the clinic reception.
        </p>
    </section>

    <section class="card">
        <div class="card-head">
            <h2>My appointments</h2>
            <a class="button primary"
               href="${pageContext.request.contextPath}/app/my/request">Request appointment</a>
        </div>

        <%--
            The status shown is read from the database on every page load, through
            facade().myHistory(actor). It is not cached in the session and not held in any
            application-scope map: there is exactly one authoritative status for an appointment and
            it lives in the appointments table. A request approved by reception a second ago
            therefore reads CONFIRMED here on the next refresh, with nothing to synchronise.
        --%>
        <c:choose>
            <c:when test="${empty appointments}">
                <p class="empty">
                    You have no appointments yet.
                    <a href="${pageContext.request.contextPath}/app/my/request">Request one</a>,
                    or telephone the clinic.
                </p>
            </c:when>
            <c:otherwise>
                <table class="data-table">
                    <caption class="visually-hidden">Your appointments, most recent first</caption>
                    <thead>
                        <tr>
                            <th scope="col">Reference</th>
                            <th scope="col">Date</th>
                            <th scope="col">Time</th>
                            <th scope="col">Dentist</th>
                            <th scope="col">Treatment</th>
                            <th scope="col">Status</th>
                            <th scope="col">From the clinic</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach var="appointment" items="${appointments}">
                            <tr>
                                <td><c:out value="${appointment.appointmentNo}"/></td>
                                <td><c:out value="${appointment.appointmentDate}"/></td>
                                <td><c:out value="${appointment.appointmentTime}"/></td>
                                <td><c:out value="${appointment.dentist.dentistName}"/></td>
                                <td><c:out value="${appointment.treatment.treatmentName}"/></td>
                                <td><t:statusPill status="${appointment.status}"/></td>
                                <td>
                                    <%--
                                        Only a declined request explains itself. A cancellation
                                        reason recorded by staff about a visit is an internal note
                                        and stays on the staff view; a rejection reason is written
                                        to be read by the patient, which is the whole point of
                                        requiring one.
                                    --%>
                                    <c:choose>
                                        <c:when test="${appointment.status eq 'REQUESTED'}">
                                            <small>Waiting for the clinic to confirm.</small>
                                        </c:when>
                                        <c:when test="${appointment.status eq 'REJECTED'}">
                                            <small><c:out value="${appointment.cancelReason}"/></small>
                                        </c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </c:otherwise>
        </c:choose>
    </section>

</t:page>
