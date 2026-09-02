<%--
    The reminder worklist.

    Two kinds of entry, and the order is the point. Appointments whose slot has passed with no
    outcome recorded come first, because each one is actively blocking something: it cannot be
    billed, and it is missing from every report that counts completed visits. Upcoming
    appointments follow, so tomorrow's patients can be confirmed in advance - which is how no-shows
    and the idle chair time they cause are reduced.

    Each entry links straight to the appointment, because reading the reminder and acting on it
    should not be two separate hunts.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Reminder queue"
        subtitle="What needs chasing, most urgent first." wide="true">

    <div class="bar-form split">
        <form method="get" action="${pageContext.request.contextPath}/app/reminders"
              class="bar-form">
            <label for="days">Look ahead</label>
            <select id="days" name="days">
                <option value="1" ${lookAheadDays eq 1 ? 'selected' : ''}>Tomorrow</option>
                <option value="3" ${lookAheadDays eq 3 ? 'selected' : ''}>3 days</option>
                <option value="7" ${lookAheadDays eq 7 ? 'selected' : ''}>A week</option>
                <option value="30" ${lookAheadDays eq 30 ? 'selected' : ''}>A month</option>
            </select>
            <button type="submit" class="button">Show</button>
        </form>

        <span class="result-count">
            ${reminders.size()} ${reminders.size() eq 1 ? 'reminder' : 'reminders'}<c:if
                test="${overdueCount gt 0}">, ${overdueCount} overdue</c:if>
        </span>
    </div>

    <c:choose>
        <c:when test="${empty reminders}">
            <section class="panel-card">
                <p class="empty">
                    Nothing needs chasing. Every slot that has passed has an outcome recorded, and
                    nothing is due inside the window you chose.
                </p>
            </section>
        </c:when>

        <c:otherwise>
            <div class="reminder-cards">
                <c:forEach var="reminder" items="${reminders}">
                    <article class="panel-card reminder-card ${reminder.overdue ? 'overdue' : ''}">
                        <header class="card-head">
                            <h2>
                                <a href="${pageContext.request.contextPath}/app/appointments/view?no=${reminder.appointment.appointmentNo}">
                                    <c:out value="${reminder.appointment.patient.patientName}"/>
                                </a>
                            </h2>
                            <span class="pill ${reminder.overdue ? 'status-NO_SHOW' : 'status-SCHEDULED'}">
                                <c:out value="${reminder.type.label}"/>
                            </span>
                        </header>

                        <dl class="detail-grid tight">
                            <div>
                                <dt>When</dt>
                                <dd>
                                    ${reminder.appointment.appointmentDate}
                                    at ${reminder.appointment.appointmentTime}
                                    &mdash;
                                    <strong><c:out value="${reminder.timingDescription}"/></strong>
                                </dd>
                            </div>
                            <div>
                                <dt>Appointment</dt>
                                <dd class="mono"><c:out value="${reminder.appointment.appointmentNo}"/></dd>
                            </div>
                            <div>
                                <dt>Dentist</dt>
                                <dd><c:out value="${reminder.appointment.dentist.dentistName}"/></dd>
                            </div>
                            <div>
                                <dt>Treatment</dt>
                                <dd><c:out value="${reminder.appointment.treatment.treatmentName}"/></dd>
                            </div>
                            <div>
                                <dt>Contact</dt>
                                <dd class="nowrap">
                                    <c:out value="${reminder.appointment.patient.contactNumber}"/>
                                </dd>
                            </div>
                        </dl>

                        <p class="reminder-hint"><c:out value="${reminder.type.actionHint}"/></p>

                        <div class="actions">
                            <a class="button ghost small"
                               href="${pageContext.request.contextPath}/app/appointments/view?no=${reminder.appointment.appointmentNo}">
                                ${reminder.overdue ? 'Record the outcome' : 'Open the appointment'}
                            </a>
                        </div>
                    </article>
                </c:forEach>
            </div>
        </c:otherwise>
    </c:choose>

</t:page>
