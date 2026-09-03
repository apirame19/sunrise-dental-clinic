<%--
    The patient's receipt.

    Rendered without the application shell so that the browser's own print command produces
    something that can be handed over: no navigation, no sign-out button, no menu. The print rules
    in main.css remove the on-screen buttons too.

    Every figure here is the snapshot stored on the bill, never a recalculation. If the clinic
    raises the price of a root canal next month, this receipt must still say what the patient
    actually paid.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Receipt <c:out value="${bill.billNo}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/main.css">
</head>
<body class="receipt-page">

<main class="receipt">

    <header class="receipt-head">
        <div>
            <h1><c:out value="${clinic.clinicName}"/></h1>
            <p class="receipt-clinic">
                <c:out value="${clinic.clinicAddress}"/><br>
                <c:out value="${clinic.clinicPhone}"/>
            </p>
        </div>
        <div class="receipt-meta">
            <p class="receipt-number"><c:out value="${bill.billNo}"/></p>
            <p>Issued <c:out value="${bill.generatedAt}"/></p>
            <c:if test="${not empty bill.generatedByName}">
                <p>by <c:out value="${bill.generatedByName}"/></p>
            </c:if>
        </div>
    </header>

    <section class="receipt-parties">
        <c:if test="${not empty bill.appointment}">
            <div>
                <h2>Patient</h2>
                <p>
                    <c:out value="${bill.appointment.patient.patientName}"/><br>
                    <c:out value="${bill.appointment.patient.address}"/><br>
                    <c:out value="${bill.appointment.patient.contactNumber}"/>
                </p>
            </div>
            <div>
                <h2>Visit</h2>
                <p>
                    <c:out value="${bill.appointmentNo}"/><br>
                    <c:out value="${bill.appointment.appointmentDate}"/>
                    at <c:out value="${bill.appointment.appointmentTime}"/><br>
                    <c:out value="${bill.appointment.dentist.dentistName}"/><br>
                    <c:out value="${bill.appointment.treatment.treatmentName}"/>
                </p>
            </div>
        </c:if>
        <c:if test="${empty bill.appointment}">
            <div>
                <h2>Visit</h2>
                <p><c:out value="${bill.appointmentNo}"/></p>
            </div>
        </c:if>
    </section>

    <table class="receipt-lines">
        <caption class="visually-hidden">Itemised charges</caption>
        <thead>
        <tr>
            <th scope="col">Item</th>
            <th scope="col">Description</th>
            <th scope="col" class="right">Amount</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="line" items="${bill.lines}">
            <tr class="${line.lineType.deduction ? 'deduction' : ''}">
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
            <td colspan="2" class="right">Total paid</td>
            <td class="right nowrap mono strong">
                <c:out value="${currency}"/>
                <fmt:formatNumber value="${bill.totalAmount}" minFractionDigits="2"
                                  maxFractionDigits="2" groupingUsed="true"/>
            </td>
        </tr>
        </tfoot>
    </table>

    <p class="receipt-thanks">
        Thank you for visiting <c:out value="${clinic.clinicName}"/>.
        This receipt is a record of the amount charged for the visit shown above.
    </p>

    <div class="actions no-print">
        <a class="button primary"
           href="${pageContext.request.contextPath}/app/appointments/view?no=${bill.appointmentNo}">
            Back to the appointment
        </a>
        <a class="button ghost" href="${pageContext.request.contextPath}/app/billing">
            Bill another visit
        </a>
    </div>

</main>

</body>
</html>
