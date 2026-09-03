<%--
    Search for an appointment by its number.

    An appointment that does not exist produces a "nothing found" message on this page, not a 404
    error screen. Somebody mistyping a reference at the front desk has not made an error worth
    interrupting them for; they need to try again, on the same screen, with the box still in front
    of them.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Search appointments"
        subtitle="Look up a booking by its appointment number.">

    <form method="get" action="${pageContext.request.contextPath}/app/appointments/search"
          class="bar-form" role="search">
        <label for="no">Appointment number</label>
        <input type="search" id="no" name="no" required maxlength="20"
               placeholder="APT-20260811-001"
               value="<c:out value='${searchTerm}'/>">
        <button type="submit" class="button primary">Search</button>
    </form>

    <c:if test="${searched}">
        <c:choose>

            <c:when test="${empty appointment}">
                <div class="panel-card">
                    <p class="empty">
                        No appointment was found with the number
                        &ldquo;<c:out value="${searchTerm}"/>&rdquo;.
                        Check the reference and try again.
                    </p>
                </div>
            </c:when>

            <c:otherwise>
                <section class="panel-card">
                    <header class="card-head">
                        <h2><c:out value="${appointment.appointmentNo}"/></h2>
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
                            <dt>Dentist</dt>
                            <dd><c:out value="${appointment.dentist.displayName}"/></dd>
                        </div>
                        <div>
                            <dt>Treatment</dt>
                            <dd><c:out value="${appointment.treatment.treatmentName}"/></dd>
                        </div>
                        <div>
                            <dt>Date</dt>
                            <dd>${appointment.appointmentDate}</dd>
                        </div>
                        <div>
                            <dt>Time</dt>
                            <dd>${appointment.appointmentTime} &ndash; ${appointment.endTime}</dd>
                        </div>
                        <c:if test="${not empty bill}">
                            <div>
                                <dt>Bill</dt>
                                <dd>
                                    <a href="${pageContext.request.contextPath}/app/billing/receipt?billNo=${bill.billNo}">
                                        <c:out value="${bill.billNo}"/>
                                    </a>
                                    &mdash; <c:out value="${clinic.currencyCode}"/>
                                    <fmt:formatNumber value="${bill.totalAmount}"
                                                      minFractionDigits="2" maxFractionDigits="2"/>
                                </dd>
                            </div>
                        </c:if>
                    </dl>

                    <div class="actions">
                        <a class="button primary"
                           href="${pageContext.request.contextPath}/app/appointments/view?no=${appointment.appointmentNo}">
                            Open this appointment
                        </a>
                    </div>
                </section>
            </c:otherwise>

        </c:choose>
    </c:if>

</t:page>
