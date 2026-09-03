<%--
    One field's rejection message, printed immediately after its input.

    The message is tied to the input by id, so a screen reader announces it when focus reaches the
    field rather than leaving the user to hunt for a summary at the top of the page. Marking the
    input aria-invalid is the view's job; this tag supplies the text it points at.

    Renders nothing at all when the field was accepted, so a form can carry one of these after
    every input without cluttering the page that was filled in correctly.
--%>
<%@ tag description="Per-field validation message" pageEncoding="UTF-8"
        trimDirectiveWhitespaces="true" %>
<%@ attribute name="validation" required="false"
        type="lk.sunrisedental.validation.ValidationResult" %>
<%@ attribute name="field" required="true" type="java.lang.String" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:if test="${not empty validation and not empty validation.fieldErrors[field]}">
    <p class="field-error" id="${field}-error">
        <span aria-hidden="true">!</span>
        <c:out value="${validation.fieldErrors[field]}"/>
    </p>
</c:if>
