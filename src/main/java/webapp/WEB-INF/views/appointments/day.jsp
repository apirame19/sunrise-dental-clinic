<%--
    The day list: everything booked on one date, in the order the clinic works through it.

    The previous and next links are plain anchors carrying a date, so the page is bookmarkable and
    the browser's back button behaves. That is the whole interaction model of this application -
    links and forms, no script.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Appointments for the day" subtitle="${date}" wide="true">

    <div class="bar-form split">
        <form method="get" action="${pageContext.request.contextPath}/app/appointments/day"
              class="bar-form">
            <a class="button ghost small"
               href="${pageContext.request.contextPath}/app/appointments/day?date=${previousDate}">
                &larr; ${previousDate}
            </a>
            <label for="date" class="visually-hidden">Date</label>
            <input type="date" id="date" name="date" value="${date}">
            <button type="submit" class="button">Show</button>
            <a class="button ghost small"
               href="${pageContext.request.contextPath}/app/appointments/day?date=${nextDate}">
                ${nextDate} &rarr;
            </a>
        </form>

        <a class="button primary"
           href="${pageContext.request.contextPath}/app/appointments/register?date=${date}">
            Register an appointment
        </a>
    </div>

    <section class="panel-card">
        <c:choose>
            <c:when test="${empty appointments}">
                <p class="empty">Nothing is booked for <c:out value="${date}"/>.</p>
            </c:when>
            <c:otherwise>
                <div class="table-scroll">
                    <table class="grid">
                        <caption class="visually-hidden">
                            Appointments on <c:out value="${date}"/>
                        </caption>
                        <thead>
                        <tr>
                            <th scope="col">Time</th>
                            <th scope="col">Ends</th>
                            <th scope="col">Appointment</th>
                            <th scope="col">Patient</th>
                            <th scope="col">Contact</th>
                            <th scope="col">Dentist</th>
                            <th scope="col">Treatment</th>
                            <th scope="col">Status</th>
                            <th scope="col">Bill</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="appointment" items="${appointments}">
                            <tr>
                                <td class="nowrap">${appointment.appointmentTime}</td>
                                <td class="nowrap muted">${appointment.endTime}</td>
                                <td>
                                    <a href="${pageContext.request.contextPath}/app/appointments/view?no=${appointment.appointmentNo}">
                                        <c:out value="${appointment.appointmentNo}"/>
                                    </a>
                                </td>
                                <td>
                                    <a href="${pageContext.request.contextPath}/app/patients/view?id=${appointment.patient.patientId}">
                                        <c:out value="${appointment.patient.patientName}"/>
                                    </a>
                                </td>
                                <td class="nowrap"><c:out value="${appointment.patient.contactNumber}"/></td>
                                <td><c:out value="${appointment.dentist.dentistName}"/></td>
                                <td><c:out value="${appointment.treatment.treatmentName}"/></td>
                                <td><t:statusPill status="${appointment.status}"/></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${appointment.billed}">
                                            <a href="${pageContext.request.contextPath}/app/billing/receipt?billNo=${appointment.billNo}">
                                                <c:out value="${appointment.billNo}"/>
                                            </a>
                                        </c:when>
                                        <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>

</t:page>
