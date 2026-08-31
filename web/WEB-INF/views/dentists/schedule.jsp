<%--
    One dentist's day, shown as slots rather than as a list of bookings.

    A list answers "what is booked". The receptionist's actual question is "when is this dentist
    free", and only the slot view answers it - because a 90-minute treatment starting at 10:00
    blocks 10:30 and 11:00, which a list of start times does not make obvious.

    The overlap rule used to mark a slot taken is the same one the booking form applies and the
    same one fn_is_dentist_available applies in the database, so this screen cannot promise a slot
    the booking form will refuse.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="${dentist.dentistName}" subtitle="${dentist.specialization} &middot; ${date}"
        wide="true">

    <div class="bar-form split">
        <form method="get" action="${pageContext.request.contextPath}/app/dentists/schedule"
              class="bar-form">
            <input type="hidden" name="id" value="${dentist.dentistId}">
            <a class="button ghost small"
               href="${pageContext.request.contextPath}/app/dentists/schedule?id=${dentist.dentistId}&amp;date=${previousDate}">
                &larr; ${previousDate}
            </a>
            <label for="date" class="visually-hidden">Date</label>
            <input type="date" id="date" name="date" value="${date}">
            <button type="submit" class="button">Show</button>
            <a class="button ghost small"
               href="${pageContext.request.contextPath}/app/dentists/schedule?id=${dentist.dentistId}&amp;date=${nextDate}">
                ${nextDate} &rarr;
            </a>
        </form>

        <c:if test="${dentist.active and sessionUser.role.canRegisterAppointments()}">
            <a class="button primary"
               href="${pageContext.request.contextPath}/app/appointments/register?date=${date}">
                Register an appointment
            </a>
        </c:if>
    </div>

    <c:if test="${not dentist.active}">
        <p class="alert info" role="status">
            <c:out value="${dentist.dentistName}"/> has been withdrawn from the booking list.
            Historical appointments are still shown.
        </p>
    </c:if>

    <div class="two-column">

        <section class="panel-card">
            <header class="card-head"><h2>Slots</h2></header>

            <ul class="slot-list">
                <c:forEach var="slot" items="${slots}">
                    <li class="slot ${slot.available() ? 'free' : 'taken'}">
                        <span class="slot-time">${slot.startTime()} &ndash; ${slot.endTime()}</span>
                        <c:choose>
                            <c:when test="${slot.available()}">
                                <span class="slot-state">Free</span>
                            </c:when>
                            <c:otherwise>
                                <span class="slot-state">
                                    <a href="${pageContext.request.contextPath}/app/appointments/view?no=${slot.appointmentNo()}">
                                        <c:out value="${slot.patientName()}"/>
                                    </a>
                                    <small><c:out value="${slot.treatmentName()}"/></small>
                                </span>
                            </c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
            </ul>
        </section>

        <section class="panel-card">
            <header class="card-head"><h2>Appointments</h2></header>

            <c:choose>
                <c:when test="${empty appointments}">
                    <p class="empty">
                        <c:out value="${dentist.dentistName}"/> has nothing booked on this day.
                    </p>
                </c:when>
                <c:otherwise>
                    <div class="table-scroll">
                        <table class="grid">
                            <caption class="visually-hidden">
                                Appointments for <c:out value="${dentist.dentistName}"/>
                            </caption>
                            <thead>
                            <tr>
                                <th scope="col">Time</th>
                                <th scope="col">Patient</th>
                                <th scope="col">Treatment</th>
                                <th scope="col">Status</th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="appointment" items="${appointments}">
                                <tr>
                                    <td class="nowrap">
                                        ${appointment.appointmentTime} &ndash; ${appointment.endTime}
                                    </td>
                                    <td>
                                        <a href="${pageContext.request.contextPath}/app/appointments/view?no=${appointment.appointmentNo}">
                                            <c:out value="${appointment.patient.patientName}"/>
                                        </a>
                                    </td>
                                    <td><c:out value="${appointment.treatment.treatmentName}"/></td>
                                    <td><t:statusPill status="${appointment.status}"/></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>
        </section>
    </div>

</t:page>
