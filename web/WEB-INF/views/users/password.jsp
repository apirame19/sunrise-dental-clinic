<%--
    Reset a staff member's password.

    The current password is not asked for and cannot be shown. It is stored as a salted PBKDF2
    hash, which is one-way by design - an administrator resetting an account is the recovery path,
    and the absence of any "show password" control here is the point rather than an omission.

    Resetting also clears any lockout, because the usual reason someone is locked out is that they
    have forgotten the password they are now being given a new one for.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Reset password" subtitle="${staffMember.fullName} (${staffMember.username})">

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post"
          action="${pageContext.request.contextPath}/app/users/password?id=${staffMember.userId}"
          class="stacked-form card-form" autocomplete="off">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="field">
            <label for="password">New password</label>
            <input type="password" id="password" name="password" required maxlength="200" autofocus
                   autocomplete="new-password"
                   aria-describedby="password-hint password-error"
                   <c:if test="${not empty validation.fieldErrors['password']}">aria-invalid="true"</c:if>>
            <p class="hint" id="password-hint">
                At least 10 characters, containing letters, digits and at least one other
                character. Give it to the account holder directly and ask them to change it.
            </p>
            <t:fieldError validation="${validation}" field="password"/>
        </div>

        <p class="alert info" role="note">
            Any lockout on this account is cleared at the same time.
        </p>

        <div class="actions">
            <button type="submit" class="button primary">Set the new password</button>
            <a class="button ghost" href="${pageContext.request.contextPath}/app/users">Cancel</a>
        </div>
    </form>

</t:page>
