<%--
    Price a visit, then issue the bill.

    The breakdown shown here is the Decorator chain's own output: a base treatment charge, then a
    consultation fee, then any follow-up discount, then the levy - each one a line that says what
    it is. The lines are signed, so they add up to the total printed under them. A receipt whose
    lines do not add up is the billing error the clinic came to this project with.

    The figures in this preview and the figures on the issued bill come from the same calculator
    over the same plan, so a quote given at the desk and the amount later charged cannot differ.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Billing" subtitle="Price a completed visit and issue its bill.">

    <form method="get" action="${pageContext.request.contextPath}/app/billing"
          class="bar-form" role="search">
        <label for="no">Appointment number</label>
        <input type="search" id="no" name="no" required maxlength="20"
               placeholder="APT-20260811-001" value="<c:out value='${searchTerm}'/>">
        <button type="submit" class="button primary">Look up</button>
    </form>

    <c:if test="${searched}">

        <c:if test="${alreadyIssued}">
            <p class="alert info" role="status">
                This appointment has already been billed as
                <strong><c:out value="${bill.billNo}"/></strong>. The figures below are the ones
                recorded at the time, not a fresh calculation &mdash; a receipt already given to a
                patient must never change.
            </p>
        </c:if>

        <c:if test="${not empty appointment}">
            <section class="panel-card">
                <header class="card-head">
                    <h2><c:out value="${appointment.appointmentNo}"/></h2>
                    <t:statusPill status="${appointment.status}"/>
                </header>
                <dl class="detail-grid">
                    <div>
                        <dt>Patient</dt>
                        <dd><c:out value="${appointment.patient.patientName}"/></dd>
                    </div>
                    <div>
                        <dt>Dentist</dt>
                        <dd><c:out value="${appointment.dentist.dentistName}"/></dd>
                    </div>
                    <div>
                        <dt>Treatment</dt>
                        <dd><c:out value="${appointment.treatment.treatmentName}"/></dd>
                    </div>
                    <div>
                        <dt>Date</dt>
                        <dd>${appointment.appointmentDate} at ${appointment.appointmentTime}</dd>
                    </div>
                </dl>
            </section>
        </c:if>

        <section class="panel-card">
            <header class="card-head">
                <h2>${alreadyIssued ? 'Issued bill' : 'Itemised preview'}</h2>
                <c:if test="${alreadyIssued}">
                    <a class="button ghost small"
                       href="${pageContext.request.contextPath}/app/billing/receipt?billNo=${bill.billNo}">
                        Printable receipt
                    </a>
                </c:if>
            </header>

            <table class="grid bill-lines">
                <caption class="visually-hidden">Itemised bill lines</caption>
                <thead>
                <tr>
                    <th scope="col" class="right">#</th>
                    <th scope="col">Item</th>
                    <th scope="col">Description</th>
                    <th scope="col" class="right">Amount</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="line" items="${bill.lines}">
                    <tr class="${line.lineType.deduction ? 'deduction' : ''}">
                        <td class="right muted">${line.lineNo}</td>
                        <td><c:out value="${line.lineType.label}"/></td>
                        <td><c:out value="${line.description}"/></td>
                        <td class="right nowrap mono">
                            <c:out value="${currency}"/>
                            <fmt:formatNumber value="${line.amount}" minFractionDigits="2"
                                              maxFractionDigits="2" groupingUsed="true"/>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
                <tfoot>
                <tr class="total-row">
                    <td colspan="3" class="right">Total payable</td>
                    <td class="right nowrap mono strong">
                        <c:out value="${currency}"/>
                        <fmt:formatNumber value="${bill.totalAmount}" minFractionDigits="2"
                                          maxFractionDigits="2" groupingUsed="true"/>
                    </td>
                </tr>
                </tfoot>
            </table>

            <p class="fineprint">
                <c:choose>
                    <c:when test="${bill.internallyConsistent}">
                        The lines above sum exactly to the total.
                    </c:when>
                    <c:otherwise>
                        <strong>The lines above do not sum to the total.</strong>
                        This bill will be refused when issued.
                    </c:otherwise>
                </c:choose>
            </p>

            <c:if test="${not alreadyIssued and not empty appointment}">
                <c:choose>
                    <c:when test="${appointment.status.billable}">
                        <form method="post"
                              action="${pageContext.request.contextPath}/app/billing/generate"
                              class="actions">
                            <input type="hidden" name="csrfToken" value="${csrfToken}">
                            <input type="hidden" name="no" value="${appointment.appointmentNo}">
                            <button type="submit" class="button primary">
                                Issue this bill
                            </button>
                            <a class="button ghost"
                               href="${pageContext.request.contextPath}/app/appointments/view?no=${appointment.appointmentNo}">
                                Back to the appointment
                            </a>
                        </form>
                    </c:when>
                    <c:otherwise>
                        <p class="alert error" role="alert">
                            This appointment is
                            <c:out value="${appointment.status.label}"/>.
                            Only a completed visit can be billed &mdash; a booking is not income.
                        </p>
                    </c:otherwise>
                </c:choose>
            </c:if>
        </section>
    </c:if>

</t:page>
