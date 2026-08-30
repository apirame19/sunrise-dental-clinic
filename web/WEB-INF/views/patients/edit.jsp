<%--
    Amend a patient's contact details.

    The name is shown but not editable. It forms half of the patient identity key - the pair
    (name, contact number) - so changing it here would silently split one person's history in two,
    or merge two people into one. Correcting a genuine misspelling is a data-administration task
    carried out deliberately, not something that happens by accident on a busy afternoon.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Amend patient details" subtitle="${patient.patientName}">

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post"
          action="${pageContext.request.contextPath}/app/patients/edit?id=${patient.patientId}"
          class="stacked-form card-form">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="field">
            <label for="patientName">Full name</label>
            <input type="text" id="patientName" readonly disabled
                   value="<c:out value='${patient.patientName}'/>"
                   aria-describedby="patientName-hint">
            <p class="hint" id="patientName-hint">
                The name is part of this patient's identity and cannot be changed here.
            </p>
        </div>

        <div class="field">
            <label for="address">Address</label>
            <input type="text" id="address" name="address" required maxlength="255"
                   value="<c:out value='${patient.address}'/>"
                   <c:if test="${not empty validation.fieldErrors['address']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="address"/>
        </div>

        <div class="field">
            <label for="contactNumber">Contact number</label>
            <input type="tel" id="contactNumber" name="contactNumber" required maxlength="20"
                   value="<c:out value='${patient.contactNumber}'/>"
                   <c:if test="${not empty validation.fieldErrors['contactNumber']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="contactNumber"/>
        </div>

        <div class="field">
            <label for="email">Email <span class="optional">(optional)</span></label>
            <input type="email" id="email" name="email" maxlength="120"
                   value="<c:out value='${patient.email}'/>">
            <t:fieldError validation="${validation}" field="email"/>
        </div>

        <div class="actions">
            <button type="submit" class="button primary">Save changes</button>
            <a class="button ghost"
               href="${pageContext.request.contextPath}/app/patients/view?id=${patient.patientId}">
                Cancel
            </a>
        </div>
    </form>

</t:page>
