<%--
    The banner at the top of a rejected form.

    Shows the summary sentence the ValidationResult produced ("There are 3 problems with this
    form...") and any error that belongs to the submission as a whole rather than to one field -
    a dentist who is not free at the chosen date and time together, for instance, which is not the
    fault of either field on its own.

    Per-field messages are not repeated here. They are printed beside their own inputs by
    <t:fieldError>, which is where somebody fixing them is actually looking.
--%>
<%@ tag description="Validation summary banner" pageEncoding="UTF-8"
        trimDirectiveWhitespaces="true" %>
<%@ attribute name="validation" required="true"
        type="lk.sunrisedental.validation.ValidationResult" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:if test="${validation.hasErrors()}">
    <div class="alert error" role="alert">
        <p class="alert-summary"><c:out value="${validation.summary}"/></p>

        <c:if test="${not empty validation.globalErrors}">
            <ul class="alert-list">
                <c:forEach var="message" items="${validation.globalErrors}">
                    <li><c:out value="${message}"/></li>
                </c:forEach>
            </ul>
        </c:if>
    </div>
</c:if>
