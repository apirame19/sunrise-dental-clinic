<%--
    Register a patient who is not being booked in at the same moment.

    The usual route onto the register is the booking form, which creates the patient record as part
    of the appointment. This screen exists for the other case - somebody being put on file now and
    booked later - and it runs the same three validation handlers, so a telephone number is
    normalised identically whichever way it arrives.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Register patient"
        subtitle="Put someone on file without booking them in.">

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/app/patients/register"
          class="stacked-form card-form">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="field">
            <label for="patientName">Full name</label>
            <input type="text" id="patientName" name="patientName" required maxlength="100"
                   autofocus autocomplete="off"
                   value="<c:out value='${patientName}'/>"
                   <c:if test="${not empty validation.fieldErrors['patientName']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="patientName"/>
        </div>

        <div class="field">
            <label for="address">Address</label>
            <input type="text" id="address" name="address" required maxlength="255"
                   autocomplete="off" value="<c:out value='${address}'/>"
                   <c:if test="${not empty validation.fieldErrors['address']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="address"/>
        </div>

        <div class="field">
            <label for="contactNumber">Contact number</label>
            <input type="tel" id="contactNumber" name="contactNumber" required maxlength="20"
                   autocomplete="off" value="<c:out value='${contactNumber}'/>"
                   aria-describedby="contactNumber-hint contactNumber-error"
                   <c:if test="${not empty validation.fieldErrors['contactNumber']}">aria-invalid="true"</c:if>>
            <p class="hint" id="contactNumber-hint">
                A Sri Lankan mobile or land line. The name and number together identify a patient,
                so a household can share one line without merging two people's records.
            </p>
            <t:fieldError validation="${validation}" field="contactNumber"/>
        </div>

        <div class="field">
            <label for="email">Email <span class="optional">(optional)</span></label>
            <input type="email" id="email" name="email" maxlength="120" autocomplete="off"
                   value="<c:out value='${email}'/>"
                   <c:if test="${not empty validation.fieldErrors['email']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="email"/>
        </div>

        <div class="actions">
            <button type="submit" class="button primary">Register patient</button>
            <a class="button ghost" href="${pageContext.request.contextPath}/app/patients">Cancel</a>
        </div>
    </form>

</t:page>
