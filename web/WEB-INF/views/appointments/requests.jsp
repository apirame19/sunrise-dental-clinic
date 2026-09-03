<%--
    The appointment request queue - reception and administration only.

    Every request a patient has sent that nobody has decided yet, with the patient, dentist,
    treatment, day, time and status, and the two decisions that may be taken on it.

    Both decisions are POSTs carrying the session's CSRF token, because both change clinic data.
    The appointment number is the only identifier submitted; who is deciding comes from the session
    and is never a field on this page.

    Approval can legitimately fail - the slot may have gone or its date may have passed while the
    request sat in the queue - so the outcome arrives as a flash message and the request stays here
    to be dealt with another way. It is not removed from the queue by a failed approval.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Appointment requests"
        subtitle="Requests sent by patients, waiting for a decision."
        wide="true">

    <section class="card">
        <c:choose>
            <c:when test="${empty requests}">
                <p class="empty">
                    No requests are waiting. Requests appear here the moment a patient sends one.
                </p>
            </c:when>
            <c:otherwise>
                <table class="data-table">
                    <caption class="visually-hidden">
                        Appointment requests awaiting a decision, soonest first
                    </caption>
                    <thead>
                        <tr>
                            <th scope="col">Reference</th>
                            <th scope="col">Patient</th>
                            <th scope="col">Dentist</th>
                            <th scope="col">Treatment</th>
                            <th scope="col">Date</th>
                            <th scope="col">Time</th>
                            <th scope="col">Status</th>
                            <th scope="col">Decision</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach var="request" items="${requests}">
                            <tr>
                                <td>
                                    <a href="${pageContext.request.contextPath}/app/appointments/view?no=<c:out value='${request.appointmentNo}'/>">
                                        <c:out value="${request.appointmentNo}"/>
                                    </a>
                                </td>
                                <td>
                                    <c:out value="${request.patient.patientName}"/><br>
                                    <small><c:out value="${request.patient.contactNumber}"/></small>
                                </td>
                                <td><c:out value="${request.dentist.dentistName}"/></td>
                                <td>
                                    <c:out value="${request.treatment.treatmentName}"/><br>
                                    <small>${request.treatment.durationMinutes} min</small>
                                </td>
                                <td><c:out value="${request.appointmentDate}"/></td>
                                <td><c:out value="${request.appointmentTime}"/></td>
                                <td><t:statusPill status="${request.status}"/></td>
                                <td class="decision-cell">

                                    <form method="post" class="inline-form"
                                          action="${pageContext.request.contextPath}/app/appointments/requests/approve">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <input type="hidden" name="no"
                                               value="<c:out value='${request.appointmentNo}'/>">
                                        <button type="submit" class="button primary">Approve</button>
                                    </form>

                                    <%-- The reason is required by the business tier: a patient told
                                         only "declined" has to telephone the clinic to find out
                                         what to do instead. --%>
                                    <form method="post" class="inline-form"
                                          action="${pageContext.request.contextPath}/app/appointments/requests/reject">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <input type="hidden" name="no"
                                               value="<c:out value='${request.appointmentNo}'/>">
                                        <label class="visually-hidden"
                                               for="reason-${request.appointmentId}">
                                            Reason for declining
                                            <c:out value="${request.appointmentNo}"/>
                                        </label>
                                        <input type="text" id="reason-${request.appointmentId}"
                                               name="reason" maxlength="255" required
                                               placeholder="Reason for declining">
                                        <button type="submit" class="button ghost">Decline</button>
                                    </form>

                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </c:otherwise>
        </c:choose>
    </section>

    <section class="card">
        <h2>What the decisions do</h2>
        <dl class="detail-grid">
            <dt>Approve</dt>
            <dd>
                The request becomes <strong>Confirmed</strong> and the patient sees that on their
                own page. The dentist's availability is checked again first, so a request whose
                slot has since gone cannot be confirmed by mistake.
            </dd>
            <dt>Decline</dt>
            <dd>
                The request becomes <strong>Rejected</strong> and the slot is released immediately,
                so somebody else can book that time. The reason is shown to the patient.
            </dd>
        </dl>
    </section>

</t:page>
