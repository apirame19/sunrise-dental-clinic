<%--
    The signed-in dentist's own schedule.

    There is no dentist id in the URL that reaches this page and none in the links below. The
    roster entry is resolved from the session by the facade, so a dentist has nothing to edit in
    order to see a colleague's diary - the date range is the only thing this page accepts.

    Read-only. A visit outcome is recorded on the appointment details page, which already carries
    that authority; putting the status buttons here as well would mean two places to keep the
    lifecycle rules right.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="My schedule"
        subtitle="Your own appointments. Nobody else's."
        wide="true">

    <section class="card">
        <h2><c:out value="${dentist.dentistName}"/></h2>
        <dl class="detail-grid">
            <dt>Specialisation</dt>
            <dd><c:out value="${dentist.specialization}"/></dd>
            <dt>Showing</dt>
            <dd><c:out value="${from}"/> to <c:out value="${to}"/></dd>
        </dl>

        <form method="get" action="${pageContext.request.contextPath}/app/schedule"
              class="inline-form">
            <label for="from">From</label>
            <input type="date" id="from" name="from" value="${from}">
            <label for="to">To</label>
            <input type="date" id="to" name="to" value="${to}">
            <button type="submit" class="button">Show</button>
        </form>
    </section>

    <section class="card">
        <h2>Appointments</h2>

        <c:choose>
            <c:when test="${empty appointments}">
                <p class="empty">
                    You have no appointments between <c:out value="${from}"/> and
                    <c:out value="${to}"/>.
                </p>
            </c:when>
            <c:otherwise>
                <table class="data-table">
                    <caption class="visually-hidden">
                        Your appointments, earliest first
                    </caption>
                    <thead>
                        <tr>
                            <th scope="col">Date</th>
                            <th scope="col">Time</th>
                            <th scope="col">Patient</th>
                            <th scope="col">Treatment</th>
                            <th scope="col">Status</th>
                            <th scope="col">Reference</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach var="appointment" items="${appointments}">
                            <tr>
                                <td><c:out value="${appointment.appointmentDate}"/></td>
                                <td>
                                    <c:out value="${appointment.appointmentTime}"/>
                                    &ndash;
                                    <c:out value="${appointment.endTime}"/>
                                </td>
                                <td>
                                    <c:out value="${appointment.patient.patientName}"/><br>
                                    <small><c:out value="${appointment.patient.contactNumber}"/></small>
                                </td>
                                <td><c:out value="${appointment.treatment.treatmentName}"/></td>
                                <td><t:statusPill status="${appointment.status}"/></td>
                                <td>
                                    <a href="${pageContext.request.contextPath}/app/appointments/view?no=<c:out value='${appointment.appointmentNo}'/>">
                                        <c:out value="${appointment.appointmentNo}"/>
                                    </a>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </c:otherwise>
        </c:choose>
    </section>

</t:page>
