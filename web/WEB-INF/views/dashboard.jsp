<%--
    The dashboard: the day at a glance, the day's list, and the reminder queue.

    Everything on this page came from one facade call, so every figure describes the same instant.
    The view performs no arithmetic and issues no queries - the counts, the takings and the
    "awaiting outcome" total were all worked out in the business tier.

    The takings tile is absent, not zero, for a role that may not see it. Zero is a number a
    receptionist would reasonably believe.

    DashboardSnapshot is a record, so its accessors are called as methods - snapshot.date() rather
    than snapshot.date. A record's accessor is not a JavaBeans getter, and relying on the container
    to resolve one as a property would work on some servers and quietly fail on others.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<c:set var="day" value="${snapshot.date()}"/>
<c:set var="reminders" value="${snapshot.reminders()}"/>

<t:page title="Dashboard" subtitle="${day}" wide="true">

    <form method="get" action="${pageContext.request.contextPath}/app/dashboard" class="bar-form">
        <label for="date">Show the day</label>
        <input type="date" id="date" name="date" value="${day}">
        <button type="submit" class="button">Go</button>
    </form>

    <section class="tiles" aria-label="Figures for the day">

        <div class="tile">
            <span class="tile-figure">${snapshot.todayTotal()}</span>
            <span class="tile-label">Appointments</span>
        </div>
        <div class="tile">
            <span class="tile-figure">${snapshot.todayScheduled()}</span>
            <span class="tile-label">Still scheduled</span>
        </div>
        <div class="tile ok">
            <span class="tile-figure">${snapshot.todayCompleted()}</span>
            <span class="tile-label">Completed</span>
        </div>
        <div class="tile">
            <span class="tile-figure">${snapshot.todayCancelled()}</span>
            <span class="tile-label">Cancelled</span>
        </div>
        <div class="tile warn">
            <span class="tile-figure">${snapshot.todayNoShow()}</span>
            <span class="tile-label">No shows</span>
        </div>

        <c:if test="${snapshot.revenueVisible()}">
            <div class="tile money">
                <span class="tile-figure">
                    <c:out value="${clinic.currencyCode}"/>
                    <fmt:formatNumber value="${snapshot.todayRevenue()}" minFractionDigits="2"
                                      maxFractionDigits="2" groupingUsed="true"/>
                </span>
                <span class="tile-label">Billed today</span>
            </div>
        </c:if>
    </section>

    <section class="tiles secondary" aria-label="Clinic totals">
        <div class="tile plain">
            <span class="tile-figure">${snapshot.totalPatients()}</span>
            <span class="tile-label">Patients on file</span>
        </div>
        <div class="tile plain">
            <span class="tile-figure">${snapshot.activeDentists()}</span>
            <span class="tile-label">Dentists practising</span>
        </div>
        <div class="tile plain">
            <span class="tile-figure">${snapshot.upcomingScheduled()}</span>
            <span class="tile-label">Upcoming bookings</span>
        </div>
        <div class="tile plain ${snapshot.awaitingOutcomeCount() gt 0 ? 'warn' : ''}">
            <span class="tile-figure">${snapshot.awaitingOutcomeCount()}</span>
            <span class="tile-label">Awaiting an outcome</span>
        </div>
    </section>

    <div class="two-column">

        <section class="panel-card">
            <header class="card-head">
                <h2>Appointments on <c:out value="${day}"/></h2>
                <a class="button ghost small"
                   href="${pageContext.request.contextPath}/app/appointments/day?date=${day}">
                    Full day list
                </a>
            </header>

            <c:choose>
                <c:when test="${snapshot.quietDay}">
                    <p class="empty">Nothing is booked for this day.</p>
                </c:when>
                <c:otherwise>
                    <div class="table-scroll">
                        <table class="grid">
                            <caption class="visually-hidden">Appointments for the selected day</caption>
                            <thead>
                            <tr>
                                <th scope="col">Time</th>
                                <th scope="col">Appointment</th>
                                <th scope="col">Patient</th>
                                <th scope="col">Dentist</th>
                                <th scope="col">Treatment</th>
                                <th scope="col">Status</th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="appointment" items="${snapshot.todayAppointments()}">
                                <tr>
                                    <td class="nowrap">${appointment.appointmentTime}</td>
                                    <td>
                                        <a href="${pageContext.request.contextPath}/app/appointments/view?no=${appointment.appointmentNo}">
                                            <c:out value="${appointment.appointmentNo}"/>
                                        </a>
                                    </td>
                                    <td><c:out value="${appointment.patient.patientName}"/></td>
                                    <td><c:out value="${appointment.dentist.dentistName}"/></td>
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

        <section class="panel-card">
            <header class="card-head">
                <h2>Reminder queue</h2>
                <a class="button ghost small"
                   href="${pageContext.request.contextPath}/app/reminders">All reminders</a>
            </header>

            <c:choose>
                <c:when test="${empty reminders}">
                    <p class="empty">
                        Nothing needs chasing. Every slot that has passed has an outcome recorded.
                    </p>
                </c:when>
                <c:otherwise>
                    <ul class="reminder-list">
                        <c:forEach var="reminder" items="${reminders}" end="7">
                            <li class="${reminder.overdue ? 'overdue' : ''}">
                                <div class="reminder-head">
                                    <a href="${pageContext.request.contextPath}/app/appointments/view?no=${reminder.appointment.appointmentNo}">
                                        <c:out value="${reminder.appointment.patient.patientName}"/>
                                    </a>
                                    <span class="pill small"><c:out value="${reminder.type.label}"/></span>
                                </div>
                                <p class="reminder-detail">
                                    <c:out value="${reminder.appointment.treatment.treatmentName}"/>
                                    with <c:out value="${reminder.appointment.dentist.dentistName}"/>
                                    &middot;
                                    <strong><c:out value="${reminder.timingDescription}"/></strong>
                                </p>
                                <p class="reminder-hint"><c:out value="${reminder.type.actionHint}"/></p>
                            </li>
                        </c:forEach>
                    </ul>
                    <c:if test="${reminders.size() gt 8}">
                        <p class="empty">
                            and ${reminders.size() - 8} more in the full queue.
                        </p>
                    </c:if>
                </c:otherwise>
            </c:choose>
        </section>
    </div>

</t:page>
