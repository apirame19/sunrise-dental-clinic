<%--
    Add or amend a dentist. One form serves both, because the fields are identical and two files
    would drift apart the first time a field was added to one of them.

    Which of the two it is comes from whether a dentist was put in scope: an existing record posts
    to /edit carrying its id, a new one posts to /add.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<c:set var="editing" value="${not empty dentist and dentist.dentistId gt 0}"/>

<t:page title="${editing ? 'Amend dentist' : 'Add a dentist'}"
        subtitle="${editing ? dentist.dentistName : 'A new member of the practice.'}">

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post"
          action="${pageContext.request.contextPath}/app/dentists/${editing ? 'edit' : 'add'}${editing ? '?id='.concat(dentist.dentistId) : ''}"
          class="stacked-form card-form">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="field">
            <label for="dentistName">Full name</label>
            <input type="text" id="dentistName" name="dentistName" required maxlength="100"
                   autofocus value="<c:out value='${dentist.dentistName}'/>"
                   <c:if test="${not empty validation.fieldErrors['dentistName']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="dentistName"/>
        </div>

        <div class="field">
            <label for="specialization">Specialisation</label>
            <input type="text" id="specialization" name="specialization" required maxlength="100"
                   value="<c:out value='${dentist.specialization}'/>"
                   aria-describedby="specialization-hint specialization-error"
                   <c:if test="${not empty validation.fieldErrors['specialization']}">aria-invalid="true"</c:if>>
            <p class="hint" id="specialization-hint">
                For example General Dentistry, Orthodontics, Oral Surgery.
            </p>
            <t:fieldError validation="${validation}" field="specialization"/>
        </div>

        <div class="field">
            <label for="licenseNo">SLMC registration number</label>
            <input type="text" id="licenseNo" name="licenseNo" required maxlength="30"
                   value="<c:out value='${dentist.licenseNo}'/>"
                   aria-describedby="licenseNo-hint licenseNo-error"
                   <c:if test="${not empty validation.fieldErrors['licenseNo']}">aria-invalid="true"</c:if>>
            <p class="hint" id="licenseNo-hint">
                Must be unique. Two records sharing a registration number would be the same person
                entered twice.
            </p>
            <t:fieldError validation="${validation}" field="licenseNo"/>
        </div>

        <div class="field">
            <label for="contactNumber">Contact number <span class="optional">(optional)</span></label>
            <input type="tel" id="contactNumber" name="contactNumber" maxlength="20"
                   value="<c:out value='${dentist.contactNumber}'/>"
                   <c:if test="${not empty validation.fieldErrors['contactNumber']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="contactNumber"/>
        </div>

        <div class="actions">
            <button type="submit" class="button primary">
                ${editing ? 'Save changes' : 'Add dentist'}
            </button>
            <a class="button ghost" href="${pageContext.request.contextPath}/app/dentists">Cancel</a>
        </div>
    </form>

</t:page>
